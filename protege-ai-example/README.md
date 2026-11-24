# Example tool plugin

This plugin adds two tools to each ontology window. It has no agent or chat of
its own and is not bundled with the desktop application.

| Tool | What it does |
| --- | --- |
| `example_ontology_counts` | Counts asserted classes and axioms in the active ontology, excluding imports. |
| `example_class_reference` | Returns an `OWLClass` unchanged to demonstrate conversion between Java and JSON. |

## Build and install

Use JDK 25. From the repository root:

```sh
mvn -pl protege-ai-example -am -Dmaven.test.skip=true clean install
```

Copy `protege-ai-example/target/protege-ai-example-6.0.0-alpha-1.jar` into the
`plugins` directory of a desktop build containing `protege-ai-api` and
`protege-ai-core`, then restart. On macOS, use `Protégé.app/Contents/plugins`.

## Try the tools

The Java snippets below belong in an agent or other plugin with access to an
`OWLEditorKit`. For runnable examples without opening Protégé, see
[OntologyCountsToolTest](src/test/java/org/protege/ai/example/OntologyCountsToolTest.java)
and [ClassReferenceToolTest](src/test/java/org/protege/ai/example/ClassReferenceToolTest.java).
After the build above, run them from the repository root:

```sh
mvn -pl protege-ai-example test
```

Obtain the registry on the Swing thread and keep it for later calls:

```java
ToolRegistry registry = AgentToolRegistry.getRegistry(editorKit);
```

Calls run on the caller's thread. Use a worker thread for expensive work.

**Count ontology content:**

```java
AgentToolResult counts = registry.execute("example_ontology_counts", "{}");
```

On success, `counts.content()` is JSON such as
`{"classCount":98,"axiomCount":930}`. The values depend on the active ontology.
Changing the active ontology changes the next call's target.

**Convert a class reference:**

```java
AgentToolResult result = registry.execute("example_class_reference",
        "\"https://example.org/Pizza\"");

if (result instanceof AgentToolResult.Success<?> success) {
    OWLClass owlClass = OWLClass.class.cast(success.value());
    String json = success.json();
}
```

The result contains an `OWLClass` and the JSON string `"https://example.org/Pizza"`.
The tool does not check whether that class exists or change the ontology.
Relative IRIs such as `"Pizza"` fail validation.

Failed registry calls return `ToolFailure` with a status, code, message, and field
details where available. Tools report expected failures by throwing
`AgentToolException`, for example when a required reasoner is unavailable.
Unexpected exception messages stay in the application log. Treat
successful result objects as read-only. See the [registry guide](../protege-ai-core/README.md)
for threading and lifecycle rules.

## Use this plugin as a starting point

| File | What to copy or adapt |
| --- | --- |
| [ClassReferenceTool](src/main/java/org/protege/ai/example/ClassReferenceTool.java) | A minimal tool with one shared definition and an `execute` method. |
| [ClassReferenceToolFactory](src/main/java/org/protege/ai/example/ClassReferenceToolFactory.java) | A factory that exposes the definition and creates a tool. |
| [OntologyCountsTool](src/main/java/org/protege/ai/example/OntologyCountsTool.java) | A tool that looks up the active ontology once per call. |
| [OntologyCountsToolFactory](src/main/java/org/protege/ai/example/OntologyCountsToolFactory.java) | Passing a dependency from the editor kit into a tool. |
| [plugin.xml](src/main/resources/plugin.xml) | One extension entry per tool, each pointing to its factory. |

The factory and tool return the same definition. Core handles JSON and generates
the schemas. Tool authors supply ordinary Java argument and result types.
See [input and result rules](../protege-ai-core/docs/tool-contract.md) for supported
types, defaults, and nulls.

The tests run without a window. They check ontology switching and verify that
class conversion leaves Pizza's axioms unchanged, even for an unknown class IRI.
