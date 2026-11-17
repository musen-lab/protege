# Example tool plugin

This is a separate OSGi plugin, built by the reactor but not bundled with the
desktop application. It contributes `example_ontology_counts` through `plugin.xml`.
The tool reports asserted class and axiom counts in the active ontology, excluding
imports. It has no agent or chat of its own.

Build with Java 25:

```sh
mvn -pl protege-ai-example -am -Dmaven.test.skip=true clean install
```

Copy `target/protege-ai-example-6.0.0-alpha-1.jar` into the `plugins` directory of a
desktop build containing `protege-ai-api` and `protege-ai-core`, then restart.
On macOS, use `Protégé.app/Contents/plugins`.

For the editor kit of an open ontology window:

```java
AgentToolRegistry registry = AgentToolRegistry.getRegistry(editorKit);
AgentToolSchemas schemas = registry.findSchemas("example_ontology_counts").orElseThrow();
AgentToolResult result = registry.execute("example_ontology_counts", "{}");
if (result instanceof AgentToolResult.Success<?> success) {
    OntologyCountsResult counts = OntologyCountsResult.class.cast(success.value());
    String json = success.json();
}
```

A successful response retains the original `OntologyCountsResult` and JSON such
as `{"classCount":98,"axiomCount":945}`;
the values depend on the ontology. Opening another window creates another tool
instance, and changing the active ontology changes the next invocation's target.
Failures carry a status and message, without a result object. `content()` still
returns the JSON on success or the error message on failure.
Treat the returned object as read-only and copy before modifying it. Tools must
not mutate returned results, even during disposal. This example's result is a
record containing only integer counts; other tools must also protect nested
collections and objects. Core retains the original reference and does not
enforce deep immutability.

To add your own tool, depend on `protege-ai-api`, implement `AgentToolFactory<I, O>`, and
declare an extension following this plugin's `src/main/resources/plugin.xml`.
Declare the name, description, and argument/result classes once in a shared
`AgentToolDefinition`. Here, `OntologyCountsTool.DEFINITION` is returned by both
the factory's and tool's `getDefinition()` methods; the XML only identifies the
factory class. The tool inherits `getName()`, `getDescription()`, and
`getInputType()`, which read that definition. Static metadata is shared across
windows; each tool instance still has its own ontology supplier.
For this example, `OntologyCountsArguments` is an empty record and
`OntologyCountsResult` contains `classCount` and `axiomCount`. The tool returns that
record; the registry converts it to JSON. No handwritten schema or JSON is needed
in the factory or tool.
The registry generates and caches both schemas during registration. The input
schema describes an empty object; the output schema requires the two integer
counts. The registry validates both the arguments and the serialized result.
This example has no optional arguments. Other tools can use
`@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "42")` for an omitted
integer argument that should become 42, and `@ToolNullable` to permit null.
An optional argument without a default must explicitly permit null.
Each extension gets a new factory instance per window, so factory instance fields
are separate while static fields are shared across windows.
The factory receives an editor kit and can inject a model manager or an ontology
supplier into the tool. The tool implements `AgentTool<I, O>`; listener setup and cleanup
belong in `initialise` and `dispose`.
