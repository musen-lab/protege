# Agent tools

`protege-ai-api` defines typed tool contracts, factories, lifecycle, results, and the
`ToolRegistry` interface used by agents and adapters.
`protege-ai-core` discovers extensions and owns a registry for each `OWLEditorKit`.
Neither module depends on an AI framework. The API depends on Protégé's editor
modules because factories receive an editor kit and tools use its plugin lifecycle.
The editor modules do not depend on these AI modules.

## Calling tools

Call `getRegistry` on the Swing thread, normally from an editor-kit hook's
`initialise()`, and keep the returned reference for later calls.
The editor kit forwards put/get to the model manager's unsynchronized map, which
Protégé writes on the Swing thread.

```java
ToolRegistry registry = AgentToolRegistry.getRegistry(editorKit);
List<AgentToolDefinition<?, ?>> tools = registry.listTools();
AgentToolSchemas schemas = registry.findSchemas("example_ontology_counts").orElseThrow();
AgentToolResult result = registry.execute("example_ontology_counts", "{}");
```

Desktop wiring uses `protege-ai-core` to obtain the window's registry, then passes
the `ToolRegistry` interface to agents. Agents need only the API module.

An editor-kit hook creates the registry at window startup. `getRegistry` also
initializes it if another hook requests it first. Descriptors are ordered by
extension ID before registration. Duplicate tool names are rejected before the
second tool is constructed, with both source IDs logged. A name stays claimed
even if its first source fails to initialize; a duplicate cannot silently replace
it. Other names can still load.

Each accepted factory creates a fresh tool for that window. The registry calls
`initialise`, then `dispose` when the editor kit closes. Tools should release any
listeners in `dispose`. Plugin installation changes take effect in newly created
windows; there is no live registry refresh yet.
Factories and `initialise` must be quick and must not require an active ontology:
they run during window construction, before an ontology is loaded.

## Ontology context

A factory receives the window's `OWLEditorKit` and passes the dependencies its tool
needs to the tool's constructor. Resolve the active ontology once at invocation
time, rather than capturing it when the window opens. Switching the active
ontology does not recreate the registry. A separate `AgentToolContext` is not
needed for this initial API.

## Execution boundary

`execute` runs synchronously on the caller's thread, outside the registry lock.
Calls in one window may run concurrently, including calls to the same tool, so
tools must support concurrent invocation. Callers must arrange suitable threading;
expensive work must not run on Swing's event thread.

Closing the registry rejects new calls and disposes idle tools without waiting
for running calls. Each busy tool is disposed once, on the thread completing its
last call. Tool disposal must be quick and must arrange any necessary Swing-thread
cleanup. A call that never returns delays its tool's cleanup indefinitely. Closing
does not cancel work or keep the editor kit and ontology context alive.

The registry accepts JSON text, applies input defaults, validates it against the
input schema, and binds it to the definition's argument class. It serializes and
validates the tool's returned Java object against the output schema. Tools do not parse or write
JSON themselves. Unknown names return `NO_SUCH_TOOL`. Null input, malformed JSON,
validation and binding failures return `INVALID_INPUT` without invoking a tool. Tool failures
return `EXECUTION_ERROR` with their message (or failure type when no message exists),
except `VirtualMachineError`, which is rethrown. Stack traces stay in the log.
Interrupted calls preserve the thread's interrupt flag.

Validation rejects missing required fields, disallowed nulls, unknown fields, and
values of the wrong type. Parsing also rejects duplicate fields and trailing JSON.
JSON numbers such as `7.0` are valid integers; fractional and out-of-range values
are rejected before integer binding. Successful results must not be null or violate
their schema. Results are never repaired with defaults. OWL-type conversions are
not implemented yet.

Thread scheduling, timeouts, and cancellation belong to the caller or a separate
execution layer. There is no rollback or ontology-editing support.

## Contributing a tool

See [the separate example plugin](../protege-ai-example/README.md). Its factory has
a public zero-argument constructor and implements `AgentToolFactory<I, O>`. Each
`org.protege.ai.core.AgentTool` extension supplies its factory's `class`.
The factory's `getDefinition()` supplies `AgentToolDefinition<I, O>` with the tool's
`name`, `description`, `Class<I> argumentType`, and `Class<O> resultType`.
`createTool(editorKit)` returns an `AgentTool<I, O>` with the same types. The
registry exposes these definitions through `listTools` and `findTool`.
Declare the definition once and return it from both the factory's and tool's
`getDefinition()`. The tool inherits `getName()`, `getDescription()`, and
`getInputType()` from `AgentTool`; these methods read the definition, so authors
do not repeat metadata. Registration rejects and disposes a tool whose definition
differs from the factory's, before calling `initialise()`.
Reading metadata constructs the factory, but does not create a tool or need an
editor kit. Keep factory construction and metadata access quick and free of
ontology access.

Jackson binding stays inside core. Its mapper and JSON tree types are not part of
the tool API. Core exports Jackson annotations so plugins can import the same
annotation classes used by its mapper, for example `@JsonProperty` for a field name.

## Generated schemas

Registration generates input and output JSON Schemas (draft 2020-12) before
constructing the tool. `findSchemas(name)` returns the cached documents as JSON
strings, so agents need no schema-library dependency. Unsupported contracts are
rejected with their source ID and the reason in the log.

Supported types are records and Java beans, strings, booleans, numeric primitives
and wrappers, `BigInteger`, `BigDecimal`, enums, typed `List` values, and arrays
other than `byte[]` and `char[]`. Nested and recursive records are supported.
Use wrapper classes for scalar root arguments or results; primitives are supported
inside records, beans, and arrays.
Schemas require every property and exclude nulls (including list elements) unless
explicitly declared otherwise. Unknown properties are rejected; empty lists are valid.
Maps, untyped values, OWL objects, and
custom Jackson representations are not supported yet.

`@JsonProperty` supplies property and enum names. `@JsonPropertyDescription` and
`@JsonClassDescription` supply descriptions. Other Jackson annotations and
read/write access overrides are rejected rather than advertised without matching
execution support.

## Optional fields, defaults, and nulls

```java
public record SearchArguments(
        String label,
        @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"ALL\"") Scope scope,
        @JsonProperty(isRequired = OptBoolean.FALSE) @ToolNullable String note) {}
```

- `label` is required and must not be null.
- Omitting `scope` supplies `ALL`; explicit null is rejected.
- Omitting `note` supplies null; explicit null is also accepted.

Use `isRequired = OptBoolean.FALSE` to mark a property optional. The older
`required = false` cannot distinguish an explicit choice from Jackson's default,
so it does not make a tool property optional. Optional properties need a default
or permission to accept null. Required properties cannot declare defaults.

`defaultValue` is a JSON value: `"42"` in Java declares the number 42, while
`"\"ALL\""` declares the string ALL. Defaults are checked when the tool registers,
copied for each invocation, and applied only to omitted inputs, never explicit
nulls or returned results. Nested objects and lists follow the same rules.

`@ToolNullable`, from `protege-ai-api`, permits null at its exact type position.
It works for arguments and results without making a property optional.
`@ToolNullable List<String>` permits a null list; `List<@ToolNullable String>`
permits null elements. Primitive values cannot be nullable; use a wrapper such as
`Integer` instead of `int` when null is meaningful.

Schema generation uses VicTools internally with the invocation mapper. Its
libraries and the NetworkNT validator are embedded in core and are not exported
as OSGi packages. Registration checks the schemas and declared defaults before
constructing the tool. Invocation uses the cached validators.

Build dependencies once, then run just the registry and example tests:

```sh
mvn -pl protege-ai-core,protege-ai-example -am -Dmaven.test.skip=true clean install
mvn -pl protege-ai-core,protege-ai-example test
```

These tests need no window, network connection, model, or credentials.
