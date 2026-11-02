# Agent tools

`protege-ai-api` defines tool descriptions, factories, lifecycle, and results.
`protege-ai-core` discovers extensions and owns a registry for each `OWLEditorKit`.
Neither module depends on an AI framework. The API depends on Protégé's editor
modules because factories receive an editor kit and tools use its plugin lifecycle.
The editor modules do not depend on these AI modules.

## Calling tools

```java
AgentToolRegistry registry = AgentToolRegistry.getRegistry(editorKit);
List<AgentToolDescription> tools = registry.listTools();
AgentToolResult result = registry.execute("example_ontology_counts", "{}");
```

An editor-kit hook creates the registry at window startup. `getRegistry` also
initializes it if another hook requests it first. Descriptors are ordered by
extension ID before registration. Duplicate tool names are rejected before the
second factory is constructed, with both source IDs logged. A failing tool is
skipped without preventing the other tools from loading.

Each accepted factory creates a fresh tool for that window. The registry calls
`initialise`, then `dispose` when the editor kit closes. Tools should release any
listeners in `dispose`. Plugin installation changes take effect in newly created
windows; there is no live registry refresh yet.

## Ontology context

A factory receives the window's `OWLEditorKit` and passes the dependencies its tool
needs to the tool's constructor. Resolve the active ontology once at invocation
time, rather than capturing it when the window opens. Switching the active
ontology does not recreate the registry. A separate `AgentToolContext` is not
needed for this initial API.

## Execution boundary

`execute` is synchronous and serializes calls within a registry. Callers must
arrange suitable threading for their tools; expensive work must not run on Swing's
event thread. Disposal waits for an in-progress invocation. Unknown names and tool
exceptions return structured statuses, and interrupted calls preserve the thread's
interrupt flag.

Input schemas are metadata in this first implementation. Shared schema validation,
thread scheduling, timeouts, and cancellation belong to #56. There is no rollback
or ontology-editing support. These responsibilities remain independent of any
agent framework and can be introduced behind the registry's invocation boundary.

## Contributing a tool

See [the separate example plugin](../protege-ai-example/README.md). Its factory has
a public zero-argument constructor and implements `AgentToolFactory`. Each
`org.protege.ai.core.AgentTool` extension supplies `class`, `name`, `description`,
`inputSchema` (JSON Schema), and `resultFormat` (a media type).

Build dependencies once, then run just the registry and example tests:

```sh
mvn -pl protege-ai-core,protege-ai-example -am -Dmaven.test.skip=true install
mvn -pl protege-ai-core,protege-ai-example test
```

These tests need no window, network connection, model, or credentials.
