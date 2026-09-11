# Automatic entity declaration test files

These files test the entity declaration setting.

## Test ontologies and expected output

The test ontology directory is named `corpus/`. `DeclarationBaseline_TestCase` saves each of its
five ontologies in the corpus in the four formats: RDF/XML, Turtle, OWL/XML, and Functional Syntax.
It compares the declarations in each new document with the matching expected document in `baseline/`.

| | |
|---|---|
| Test ontologies | 5 files copied from `src/test/resources/ontologies` |
| Formats | RDF/XML (`.rdf`), Turtle (`.ttl`), OWL/XML (`.owx`), Functional (`.ofn`) |
| Expected output | 20 files |

The test compares declarations rather than the complete document. Changes to prefix order,
comments, or other content do not make the test fail.

Each test ontology must:

- contain entities, so there are declarations to compare;
- produce the same entity declarations on every run.

This test reads only the copies in `corpus/`; it does not read the original ontology files. The
copies of `amino-acid.owl` and `pizza.owl` came from `src/test/resources/ontologies`. The other three
came from `src/test/resources/ontologies/tree`. Other tests use the original files, so keeping a
separate copy prevents changes made for those tests from also changing this test.

### Updating the expected output

First determine why the declaration output changed. Do not replace the expected documents only to
make a failing test pass.

If the change is correct, run `DeclarationBaseline.main` from the module directory to rewrite the
expected documents.

## Small test files

The `fixtures/` directory contains a small ontology named `module.ttl` and the ontology it imports,
named `base.ttl`. `EntityDeclarationBehaviour_TestCase` saves the module with automatic declarations
included and left out.

| Term | Where it is declared | What the test checks |
|---|---|---|
| `module#LocalTerm` | the module | an explicit declaration is always kept |
| `base#SharedTerm` | the module and the import | the module keeps its own declaration |
| `base#MisplacedTerm` | the module | the namespace does not decide whether a declaration is kept |
| `base#Borrowed` | neither ontology | saving adds a declaration unless the preference prevents it |
| `base#BaseOnly` | the import | saving does not copy declarations from an import |

The test loads the imported ontology from the local `base.ttl` file. It does not use the network.
