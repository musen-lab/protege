# Example tool plugin

This is a separate OSGi plugin, built by the reactor but not bundled with the
desktop application. It contributes `example_ontology_counts` through `plugin.xml`.
The tool reports asserted class and axiom counts in the active ontology, excluding
imports. It has no agent or chat of its own.

Build with Java 25:

```sh
mvn -pl protege-ai-example -am -Dmaven.test.skip=true install
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

To add your own tool, depend on `protege-ai-api`, implement `AgentToolFactory`, and
declare an extension following this plugin's `src/main/resources/plugin.xml`.
The factory receives an editor kit and can inject a model manager or an ontology
supplier into the tool. The tool implements `AgentTool`; listener setup and cleanup
belong in `initialise` and `dispose`.
