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
AgentToolResult result = registry.execute("example_ontology_counts", "{}");
```

A successful result contains JSON such as `{"classCount":98,"axiomCount":945}`;
the values depend on the ontology. Opening another window creates another tool
instance, and changing the active ontology changes the next invocation's target.

To add your own tool, depend on `protege-ai-api`, implement `AgentToolFactory<I, O>`, and
declare an extension following this plugin's `src/main/resources/plugin.xml`.
Keep the name, description, and argument/result classes in the factory's
`getDefinition()` method; the XML only identifies the factory class.
For this example, `OntologyCountsArguments` is an empty record and
`OntologyCountsResult` contains `classCount` and `axiomCount`. The tool returns that
record; the registry converts it to JSON. No handwritten schema or JSON is needed
in the factory or tool.
Each extension gets a new factory instance per window, so factory instance fields
are separate while static fields are shared across windows.
The factory receives an editor kit and can inject a model manager or an ontology
supplier into the tool. The tool implements `AgentTool<I, O>`; listener setup and cleanup
belong in `initialise` and `dispose`.
