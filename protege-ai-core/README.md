# Agent tools

`protege-ai-api` defines tool descriptions, factories, lifecycle, results, and the
`ToolRegistry` interface used by agents and adapters.
`protege-ai-core` discovers extensions and owns a registry for each `OWLEditorKit`.
Neither module depends on an AI framework. The API depends on Protégé's editor
modules because factories receive an editor kit and tools use its plugin lifecycle.
The editor modules do not depend on these AI modules.

## Calling tools

```java
ToolRegistry registry = AgentToolRegistry.getRegistry(editorKit);
List<AgentToolDescription> tools = registry.listTools();
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

Unknown names return `NO_SUCH_TOOL`; null input returns `INVALID_INPUT` without
invoking a tool. Tool exceptions return `EXECUTION_ERROR` with their message (or
exception type when no message exists); stack traces stay in the log. Interrupted
calls preserve the thread's interrupt flag.

Input schemas are metadata in this first implementation. Shared schema validation,
thread scheduling, timeouts, and cancellation belong to #56. There is no rollback
or ontology-editing support. These responsibilities remain independent of any
agent framework and can be introduced behind the registry's invocation boundary.

## Contributing a tool

See [the separate example plugin](../protege-ai-example/README.md). Its factory has
a public zero-argument constructor and implements `AgentToolFactory`. Each
`org.protege.ai.core.AgentTool` extension supplies its factory's `class`.
The factory's `getDescription()` supplies `name`, `description`, `inputSchema`
(JSON Schema), and `resultDescription` (output encoding, fields, and meaning).
Reading metadata constructs the factory, but does not create a tool or need an
editor kit. Keep factory construction and metadata access quick and free of
ontology access.

Build dependencies once, then run just the registry and example tests:

```sh
mvn -pl protege-ai-core,protege-ai-example -am -Dmaven.test.skip=true install
mvn -pl protege-ai-core,protege-ai-example test
```

These tests need no window, network connection, model, or credentials.
