# Tool input and result rules

[Back to the registry guide](../README.md)

Core describes Java types as JSON Schemas, checks input, and converts it into Java
objects. After a tool returns, core converts its result to JSON and checks that too.
Tool authors do not maintain separate schemas.

## Supported types

| Java type | JSON form |
| --- | --- |
| Records and Java beans | Objects with named fields |
| `String`, booleans, numbers | Strings, booleans, numbers |
| Enums | Strings containing the enum names, or names supplied by `@JsonProperty` |
| Typed `List` values and arrays | Arrays |
| `OWLClass` | An absolute IRI string |

Records and beans can contain other supported types, including nested and
recursive records. Numeric support includes primitives, wrappers, `BigInteger`,
and `BigDecimal`. Use wrapper classes for a whole input or result; primitives
are supported within records, beans, and arrays.

Maps, untyped `Object` values, raw lists, `byte[]`, `char[]`, other OWL types, and
custom Jackson conversions are not supported. Core rejects unsupported types
when the tool registers and logs the source and reason.

## Required fields, defaults, and nulls

**Fields are required and non-null unless declared otherwise.** The same rule
applies to result fields and collection elements. Empty lists are allowed.

```java
public enum Scope { DIRECT, ALL }

public record SearchArguments(
        String label,
        @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"ALL\"") Scope scope,
        @JsonProperty(isRequired = OptBoolean.FALSE) @ToolNullable String note) {}
```

| Field | If omitted | If explicitly `null` |
| --- | --- | --- |
| `label` | Error | Error |
| `scope` | Uses `ALL` | Error |
| `note` | Uses null | Accepts null |

- Use `isRequired = OptBoolean.FALSE` to make a field optional. Jackson's older
  `required = false` does not express an explicit choice, so core ignores it.
- Optional fields need either a default or `@ToolNullable`. Required fields
  cannot have defaults.
- Defaults are JSON values. In Java, `defaultValue = "42"` declares a number;
  `defaultValue = "\"ALL\""` declares a string.
- Core checks defaults at registration and applies fresh copies to omitted
  inputs, including nested values. Explicit null never selects a default.
- Defaults never repair a returned result. Invalid results fail the call.

`@ToolNullable` allows null without making a field optional. Its position matters:

| Declaration | What may be null |
| --- | --- |
| `@ToolNullable List<String>` | The list |
| `List<@ToolNullable String>` | Elements in the list |

Use a wrapper such as `Integer` when a number may be null; primitives cannot be nullable.

## Names and descriptions

Use `@JsonProperty` to name fields or enum values. Use `@JsonPropertyDescription`
for field descriptions and `@JsonClassDescription` for a class description.
Other Jackson annotations and read/write access overrides are rejected.

Jackson's mapper and JSON tree classes stay inside core. Plugins can import
Jackson annotations exported by core; they do not supply their own mapper.

## OWL class references

```java
public record ClassArguments(OWLClass owlClass) {}
```

```json
{"owlClass":"https://example.org/Pizza"}
```

Core uses the window's `OWLDataFactory` to create the class reference. On output,
it writes that class's IRI as a string. This also works inside records, beans,
lists, and arrays. The successful response retains the original Java result.

- IRIs must be absolute and well formed. Unicode and non-HTTP schemes such as
  `urn:example:Pizza` are supported.
- Strings are used literally. `pizza:Pizza` is an absolute IRI with scheme
  `pizza`, not a prefix to expand. Labels are not resolved.
- Conversion does not check ontology membership, add axioms, normalize the IRI,
  or access the network. The tool checks membership when its operation needs it.
- The schema uses `type: string`, `format: iri`, and a valid-scheme pattern.
  Core enforces these for input, output, and defaults.

Tests can supply a plain OWL API factory through
`new AgentToolRegistry(sources, dataFactory)`, without an editor kit.
The one-argument constructor uses OWL API's default factory.

## Validation and failures

For each call, core applies input defaults, validates the JSON, constructs the
Java arguments, and invokes the tool once. It then serializes and validates the
result. Only a valid result becomes `Success`, containing the object and JSON.

Input checks reject missing required fields, disallowed nulls, unknown fields,
wrong types, duplicate JSON fields, and trailing JSON. Numeric coercions must
agree with the schema: `7.0` is a valid integer, but `7.1` and out-of-range values
are rejected. A successful result cannot be null.

| Status | Meaning |
| --- | --- |
| `SUCCESS` | The result object and validated JSON are available. |
| `INVALID_INPUT` | Input parsing, validation, or conversion failed. The tool was not invoked. |
| `NO_SUCH_TOOL` | The registry has no tool with that name. |
| `TOOL_ERROR` | The tool reported an expected failure through `AgentToolException`. |
| `EXECUTION_ERROR` | An unexpected exception occurred, or the result could not be serialized or validated. |
| `INTERRUPTED` | The call was interrupted; the thread's interrupt flag is preserved. |
| `REGISTRY_CLOSED` | The registry is closed to new calls. |

Failures carry `status()`, `code()`, `content()` (the message), and `details()`.
They never expose a successful result object. The code normally matches the status
name. An expected tool failure supplies its own stable code:

```java
throw new AgentToolException("REASONER_UNAVAILABLE", "Initialize the reasoner first.");
```

This returns `TOOL_ERROR` with code `REASONER_UNAVAILABLE`. Keep the message suitable
for the caller. An optional third constructor argument accepts a list of
`AgentToolResult.ErrorDetail(field, message)` values. Each field is a JSON Pointer,
such as `/queries/0/scope`; an empty pointer means the whole input.

Schema validation returns up to three field details. A missing field may point to
its containing object, with the missing name in the message. Parsing errors and
binding failures may have no field details. Code and field locations are for
programmatic use; message wording is not a stable API.

Unexpected failures return a generic message with a diagnostic reference. The
same reference and full exception are logged; arbitrary exception messages and
stack traces are not sent to the model. `VirtualMachineError` is rethrown.
The registry does not retry calls or implement cancellation. `INTERRUPTED` reports
an actual interruption, not proof that a separate timeout or cancellation request
stopped the tool.

Existing `new Failure(status, message)` calls still work. Adapters with exhaustive
status switches must handle the new `TOOL_ERROR` and `INTERRUPTED` cases.

## Registration checks

- Extensions load in extension-ID order. A duplicate name is rejected before
  creating its tool. The log names both sources.
- A failed source keeps its claimed name. A later duplicate cannot replace it;
  tools with other names can still load.
- Core checks both schemas and defaults before constructing a tool. A mismatch
  between the factory's and tool's definitions is rejected before `initialise()`.
- Generated schemas use draft 2020-12 and are cached for the registered tool.

Core uses VicTools with the invocation mapper to generate schemas, and NetworkNT
to validate them. Those libraries are embedded in core, not exported to plugins.

## Supporting additional types

Plugins cannot yet register their own converters. For a type such as
`OWLObjectProperty`, a tool can accept and return an IRI string and handle
validation and conversion inside its implementation.

A possible extension would let the factory supply a converter and describe its
JSON form. Core would use both for conversion and schema generation, scoped to
that tool. Authors could then add types without waiting for a core update.

This is a proposal, not an available API. For now, conversions stay in core so
schema, validation, default, and null rules can be tested together. A concrete
tool requirement should guide any future converter API.

## Updating older plugins

Rebuild against the current API. Tools must return their shared definition from
`getDefinition()`. Code constructing responses must use `Success` or `Failure`
instead of `new AgentToolResult(status, content)`. Callers can still use
`status()`, `isSuccess()`, and `content()`.
