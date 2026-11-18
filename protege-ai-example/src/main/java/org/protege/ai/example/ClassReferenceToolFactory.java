package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.model.OWLClass;

public final class ClassReferenceToolFactory implements AgentToolFactory<OWLClass, OWLClass> {

    @Override
    public AgentToolDefinition<OWLClass, OWLClass> getDefinition() {
        return ClassReferenceTool.DEFINITION;
    }

    @Override
    public AgentTool<OWLClass, OWLClass> createTool(OWLEditorKit editorKit) {
        return new ClassReferenceTool();
    }
}
