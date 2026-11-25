# Agent tools

Plugins contribute tools. Agents discover and call them through a registry.
Each ontology window has its own registry and tool instances. Tools work with
Java objects. A separate JSON service handles schemas, conversion, and validation
for agents that exchange JSON with a model.

| Module | Purpose |
| --- | --- |
| `protege-ai-api` | Interfaces used by tools and agents |
| `protege-ai-core` | Plugin discovery, registry, JSON conversion, and validation |
| `protege-ai-example` | A separate plugin you can build and try |

These modules have no dependency on an AI framework. The API uses Protégé's editor
modules for plugin lifecycle and window access; editor modules do not depend on AI.

## Add a tool

Start with the [example plugin](../protege-ai-example/README.md).

1. **Define the input and result.** Use Java records or classes with
   [supported field types](docs/tool-contract.md#supported-types).
2. **Declare one `AgentToolDefinition`.** It holds the tool's name, description,
   input class, and result class. Both the tool and factory return this definition
   from `getDefinition()`.
3. **Implement `AgentTool<I, O>`.** Its `execute` method receives the input object
   and returns the result object. Core handles parsing and writing JSON.
   The tool inherits `getName()`, `getDescription()`, and `getInputType()` from its definition.
4. **Implement `AgentToolFactory<I, O>`.** Give it a public no-argument constructor.
   Return a fresh tool from `createTool(editorKit)`, passing in the dependencies
   that tool needs.
5. **Register the factory in `plugin.xml`.** Add one extension per tool:

```xml
<extension id="ClassReference" point="org.protege.ai.core.AgentTool">
    <class value="org.protege.ai.example.ClassReferenceToolFactory"/>
</extension>
```

See the [example tool](../protege-ai-example/src/main/java/org/protege/ai/example/ClassReferenceTool.java)
and [factory](../protege-ai-example/src/main/java/org/protege/ai/example/ClassReferenceToolFactory.java).

Keep factory construction, metadata access, and `initialise()` quick. An ontology
may not be loaded yet. If the tool needs the active ontology, look it up once per
call, as the [counts example](../protege-ai-example/src/main/java/org/protege/ai/example/OntologyCountsTool.java) does.

For an expected failure, throw an exception with a stable code and a message
intended for the caller:

```java
throw new AgentToolException("REASONER_UNAVAILABLE", "Initialize the reasoner first.");
```

Core returns `TOOL_ERROR` with that code and message. Unexpected exceptions return
`EXECUTION_ERROR` with a reference to the full error in the application log.
See [failure handling](docs/tool-contract.md#validation-and-failures).

## Call a tool

Obtain the services on the Swing thread, normally in a hook's `initialise()`, and
keep the references. The editor kit stores them in the model manager's
unsynchronized map, which Protégé writes on the Swing thread.

```java
ToolRegistry registry = AgentToolServices.getRegistry(editorKit);
JsonToolService jsonTools = AgentToolServices.getJsonTools(editorKit);
```

Calls run on the caller's thread. Run expensive tools on a worker thread:

```java
AgentToolResult result = jsonTools.execute("example_ontology_counts", "{}");

String modelText = result.content(); // JSON on success, error message on failure.
if (result instanceof AgentToolResult.Success<?> success) {
    Object value = success.value();  // Original Java result for application code.
    String json = success.json();
} else if (result instanceof ToolFailure failure) {
    String code = failure.code();    // Stable code for application decisions.
    var details = failure.details(); // Field locations and explanations, when available.
}
```

A failure contains a status, stable code, message, and any field details, with no
result object. Use `isSuccess()` or `status()` to check the outcome.
Read `ToolFailure.code()` for programmatic handling and `content()` for the model.
A call by name can return different Java types; check the value's type before
using tool-specific fields. See [failure statuses](docs/tool-contract.md#validation-and-failures)
for the distinction between invalid input, expected failures, and unexpected errors.

For discovery, use `registry.listTools()` or `registry.findTool(name)`. Get the
generated input and output JSON Schemas from `jsonTools.findSchemas(name)`.
Agents can depend on `protege-ai-api`; desktop code uses core to obtain the services.

Already have Java arguments? Call `registry.execute(name, arguments)`. It returns
`ToolExecutionResult.Success` with the Java result, or the same `ToolFailure` type.
This path checks the declared Java input and result classes. It does not apply
JSON schemas, field validation, or defaults. Both paths use the same tool instances.

## Lifetime and threading

| When | What happens |
| --- | --- |
| A window opens | A hook creates its registry and JSON service. Either getter also creates the pair if called before the hook. |
| A tool loads | Its factory creates a tool, and core calls `initialise()`. |
| The active ontology changes | The registry and tools stay alive. Tools must resolve the current ontology when called. |
| The window closes | Core rejects new calls and disposes idle tools. Busy tools are disposed when their last call finishes, including JSON conversion. |

- Each window gets its own factory and tool instances. Static fields are shared
  across windows, so keep window-specific state in instance fields.
- Calls may run concurrently, even on the same tool. Core does not hold the
  registry lock while a tool runs.
- `dispose()` must finish promptly. It may run on the thread finishing a call;
  arrange Swing cleanup on the Swing thread when needed.
- Closing does not cancel work or keep the ontology context alive. A call that
  never returns delays that tool's disposal indefinitely.
- **Treat returned results as read-only**, including nested lists and objects.
  Tools must not modify them after returning, even during disposal. Consumers
  must copy before modifying. Core does not copy or freeze results; JSON is a snapshot.

Plugin changes take effect in new windows. There is no live registry refresh.
Scheduling, timeouts, cancellation, rollback, and ontology editing are outside this registry.

## Input and result rules

See the [tool contract reference](docs/tool-contract.md) for supported types,
optional fields, defaults, nulls, OWL class references, and failure statuses.
It also records the current limit on custom conversions and a possible extension.

## Build and test

Use JDK 25. From the repository root, install dependencies once, then run the tests:

```sh
mvn -pl protege-ai-core,protege-ai-example -am -Dmaven.test.skip=true clean install
mvn -pl protege-ai-core,protege-ai-example test
```

The tests need no desktop window, model, or credentials. They make no network calls.
