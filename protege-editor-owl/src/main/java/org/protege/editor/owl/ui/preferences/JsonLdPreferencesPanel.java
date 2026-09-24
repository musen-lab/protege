package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.owl.model.io.jsonld.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Swing preferences panel for configuring JSON-LD serialization options.
 * Exposes preset selection, 4 categorized configuration groups covering all
 * 25 parameters, field dependency validation, and persistent storage.
 */
public class JsonLdPreferencesPanel extends OWLPreferencesPanel {

    private static final long serialVersionUID = 1L;

    // Preset & Actions
    private JComboBox<JsonLdPreset> presetComboBox;
    private JButton resetButton;

    // Group 1: Context & Vocab
    private JComboBox<ContextEmbedding> contextEmbeddingComboBox;
    private JComboBox<ContextSource> contextSourceComboBox;
    private JCheckBox includeOntologyPrefixesCheckBox;
    private JCheckBox pruneUnusedPrefixesCheckBox;
    private JCheckBox useVocabCheckBox;
    private JCheckBox includeImportsClosureCheckBox;

    // Group 2: Term Generation & Mapping
    private JComboBox<GenerateTermsFor> generateTermsForComboBox;
    private JComboBox<TermNameSource> termNameSourceComboBox;
    private JComboBox<TermCollisionPolicy> termCollisionPolicyComboBox;
    private JComboBox<TypeCoercionMode> typeCoercionModeComboBox;

    // Group 3: Document Form & Syntax Structure
    private JComboBox<DocumentForm> documentFormComboBox;
    private JCheckBox compactArraysCheckBox;
    private JCheckBox wrapInGraphArrayCheckBox;
    private JComboBox<KeyOrdering> keyOrderingComboBox;
    private JCheckBox useNativeTypesCheckBox;
    private JCheckBox omitXsdStringTypesCheckBox;
    private JCheckBox useRdfTypeCheckBox;
    private JCheckBox prettyPrintCheckBox;
    private JSpinner indentSpacesSpinner;

    // Group 4: Advanced & Node Serialization
    private JComboBox<BlankNodeLabeling> blankNodeLabelingComboBox;
    private JComboBox<BlankNodeEmbedding> blankNodeEmbeddingComboBox;
    private JComboBox<RdfListHandling> rdfListHandlingComboBox;
    private JTextField listPropertyOverridesField;
    private JCheckBox useLanguageMapsCheckBox;
    private JCheckBox nullLanguageForUntaggedCheckBox;

    private boolean isUpdatingUI = false;

    @Override
    public void initialise() throws Exception {
        createUI();
        updateUIFromPreferences(JsonLdPreferences.getInstance());
        attachListeners();
    }

    private void createUI() {
        setLayout(new BorderLayout());

        PreferencesLayoutPanel panel = new PreferencesLayoutPanel();

        // --- Top Header: Presets ---
        panel.addGroup("Preset");
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        presetComboBox = new JComboBox<>(JsonLdPreset.values());
        resetButton = new JButton("Reset to Defaults");
        presetPanel.add(presetComboBox);
        presetPanel.add(resetButton);
        panel.addGroupComponent(presetPanel);

        // --- Group 1: Context & Vocab ---
        panel.addSeparator();
        panel.addGroup("Context & Vocab");
        contextEmbeddingComboBox = new JComboBox<>(ContextEmbedding.values());
        contextSourceComboBox = new JComboBox<>(ContextSource.values());
        includeOntologyPrefixesCheckBox = new JCheckBox("Include ontology prefix mappings in @context");
        pruneUnusedPrefixesCheckBox = new JCheckBox("Prune unused prefix declarations");
        useVocabCheckBox = new JCheckBox("Define @vocab with ontology default namespace");
        includeImportsClosureCheckBox = new JCheckBox("Include imported ontologies in serialization");

        panel.addGroupComponent(new JLabel("Embedding mode:"));
        panel.addIndentedGroupComponent(contextEmbeddingComboBox);
        panel.addGroupComponent(new JLabel("Context source:"));
        panel.addIndentedGroupComponent(contextSourceComboBox);
        panel.addGroupComponent(includeOntologyPrefixesCheckBox);
        panel.addGroupComponent(pruneUnusedPrefixesCheckBox);
        panel.addGroupComponent(useVocabCheckBox);
        panel.addGroupComponent(includeImportsClosureCheckBox);

        // --- Group 2: Term Generation & Mapping ---
        panel.addSeparator();
        panel.addGroup("Term Generation");
        generateTermsForComboBox = new JComboBox<>(GenerateTermsFor.values());
        termNameSourceComboBox = new JComboBox<>(TermNameSource.values());
        termCollisionPolicyComboBox = new JComboBox<>(TermCollisionPolicy.values());
        typeCoercionModeComboBox = new JComboBox<>(TypeCoercionMode.values());

        panel.addGroupComponent(new JLabel("Generate terms for:"));
        panel.addIndentedGroupComponent(generateTermsForComboBox);
        panel.addGroupComponent(new JLabel("Term name source:"));
        panel.addIndentedGroupComponent(termNameSourceComboBox);
        panel.addGroupComponent(new JLabel("Collision policy:"));
        panel.addIndentedGroupComponent(termCollisionPolicyComboBox);
        panel.addGroupComponent(new JLabel("Type coercion mode:"));
        panel.addIndentedGroupComponent(typeCoercionModeComboBox);

        // --- Group 3: Document Structure & Formatting ---
        panel.addSeparator();
        panel.addGroup("Document Structure");
        documentFormComboBox = new JComboBox<>(DocumentForm.values());
        wrapInGraphArrayCheckBox = new JCheckBox("Wrap in top-level @graph array");
        compactArraysCheckBox = new JCheckBox("Compact single-item arrays");
        keyOrderingComboBox = new JComboBox<>(KeyOrdering.values());
        useNativeTypesCheckBox = new JCheckBox("Use native JSON types (integers, booleans)");
        omitXsdStringTypesCheckBox = new JCheckBox("Omit explicit xsd:string datatype");
        useRdfTypeCheckBox = new JCheckBox("Use @type instead of rdf:type predicate");
        prettyPrintCheckBox = new JCheckBox("Pretty-print output");
        indentSpacesSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));

        JPanel indentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        indentPanel.add(new JLabel("Indentation spaces:"));
        indentPanel.add(indentSpacesSpinner);

        panel.addGroupComponent(new JLabel("Document form:"));
        panel.addIndentedGroupComponent(documentFormComboBox);
        panel.addGroupComponent(wrapInGraphArrayCheckBox);
        panel.addGroupComponent(compactArraysCheckBox);
        panel.addGroupComponent(new JLabel("Key ordering:"));
        panel.addIndentedGroupComponent(keyOrderingComboBox);
        panel.addGroupComponent(useNativeTypesCheckBox);
        panel.addGroupComponent(omitXsdStringTypesCheckBox);
        panel.addGroupComponent(useRdfTypeCheckBox);
        panel.addGroupComponent(prettyPrintCheckBox);
        panel.addIndentedGroupComponent(indentPanel);

        // --- Group 4: Advanced & Node Serialization ---
        panel.addSeparator();
        panel.addGroup("Advanced & Nodes");
        blankNodeLabelingComboBox = new JComboBox<>(BlankNodeLabeling.values());
        blankNodeEmbeddingComboBox = new JComboBox<>(BlankNodeEmbedding.values());
        rdfListHandlingComboBox = new JComboBox<>(RdfListHandling.values());
        listPropertyOverridesField = new JTextField(30);
        useLanguageMapsCheckBox = new JCheckBox("Use @language maps for multi-lingual literals");
        nullLanguageForUntaggedCheckBox = new JCheckBox("Map untagged literals to @none language key");

        JPanel listOverridesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        listOverridesPanel.add(new JLabel("List property overrides:"));
        listOverridesPanel.add(listPropertyOverridesField);

        panel.addGroupComponent(new JLabel("Blank node labeling:"));
        panel.addIndentedGroupComponent(blankNodeLabelingComboBox);
        panel.addGroupComponent(new JLabel("Blank node embedding:"));
        panel.addIndentedGroupComponent(blankNodeEmbeddingComboBox);
        panel.addGroupComponent(new JLabel("RDF list handling:"));
        panel.addIndentedGroupComponent(rdfListHandlingComboBox);
        panel.addIndentedGroupComponent(listOverridesPanel);
        panel.addGroupComponent(useLanguageMapsCheckBox);
        panel.addGroupComponent(nullLanguageForUntaggedCheckBox);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void attachListeners() {
        presetComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !isUpdatingUI) {
                JsonLdPreset selected = (JsonLdPreset) presetComboBox.getSelectedItem();
                if (selected != null && selected != JsonLdPreset.CUSTOM) {
                    JsonLdPreferences prefs = JsonLdPreferences.getInstance();
                    prefs.applyPreset(selected);
                    updateUIFromPreferences(prefs);
                }
            }
        });

        resetButton.addActionListener(e -> {
            JsonLdPreferences prefs = JsonLdPreferences.getInstance();
            prefs.resetToDefaults();
            updateUIFromPreferences(prefs);
        });

        // Modification listener for all individual components
        Runnable onModified = () -> {
            if (!isUpdatingUI) {
                presetComboBox.setSelectedItem(JsonLdPreset.CUSTOM);
                updateEnabledStates();
            }
        };

        // Combos
        contextEmbeddingComboBox.addItemListener(e -> onModified.run());
        contextSourceComboBox.addItemListener(e -> onModified.run());
        generateTermsForComboBox.addItemListener(e -> onModified.run());
        termNameSourceComboBox.addItemListener(e -> onModified.run());
        termCollisionPolicyComboBox.addItemListener(e -> onModified.run());
        typeCoercionModeComboBox.addItemListener(e -> onModified.run());
        documentFormComboBox.addItemListener(e -> onModified.run());
        keyOrderingComboBox.addItemListener(e -> onModified.run());
        blankNodeLabelingComboBox.addItemListener(e -> onModified.run());
        blankNodeEmbeddingComboBox.addItemListener(e -> onModified.run());
        rdfListHandlingComboBox.addItemListener(e -> onModified.run());

        // Checkboxes
        includeOntologyPrefixesCheckBox.addActionListener(e -> onModified.run());
        pruneUnusedPrefixesCheckBox.addActionListener(e -> onModified.run());
        useVocabCheckBox.addActionListener(e -> onModified.run());
        includeImportsClosureCheckBox.addActionListener(e -> onModified.run());
        wrapInGraphArrayCheckBox.addActionListener(e -> onModified.run());
        compactArraysCheckBox.addActionListener(e -> onModified.run());
        useNativeTypesCheckBox.addActionListener(e -> onModified.run());
        omitXsdStringTypesCheckBox.addActionListener(e -> onModified.run());
        useRdfTypeCheckBox.addActionListener(e -> onModified.run());
        prettyPrintCheckBox.addActionListener(e -> onModified.run());
        useLanguageMapsCheckBox.addActionListener(e -> onModified.run());
        nullLanguageForUntaggedCheckBox.addActionListener(e -> onModified.run());

        // Spinner
        indentSpacesSpinner.addChangeListener(e -> onModified.run());

        // Text field
        listPropertyOverridesField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onModified.run(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onModified.run(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onModified.run(); }
        });
    }

    private void updateUIFromPreferences(JsonLdPreferences prefs) {
        isUpdatingUI = true;
        try {
            presetComboBox.setSelectedItem(prefs.getActivePreset());

            // Group 1
            contextEmbeddingComboBox.setSelectedItem(prefs.getContextEmbedding());
            contextSourceComboBox.setSelectedItem(prefs.getContextSource());
            includeOntologyPrefixesCheckBox.setSelected(prefs.isIncludeOntologyPrefixes());
            pruneUnusedPrefixesCheckBox.setSelected(prefs.isPruneUnusedPrefixes());
            useVocabCheckBox.setSelected(prefs.isUseVocab());
            includeImportsClosureCheckBox.setSelected(prefs.isIncludeImportsClosure());

            // Group 2
            generateTermsForComboBox.setSelectedItem(prefs.getGenerateTermsFor());
            termNameSourceComboBox.setSelectedItem(prefs.getTermNameSource());
            termCollisionPolicyComboBox.setSelectedItem(prefs.getTermCollisionPolicy());
            typeCoercionModeComboBox.setSelectedItem(prefs.getTypeCoercionMode());

            // Group 3
            documentFormComboBox.setSelectedItem(prefs.getDocumentForm());
            wrapInGraphArrayCheckBox.setSelected(prefs.isWrapInGraphArray());
            compactArraysCheckBox.setSelected(prefs.isCompactArrays());
            keyOrderingComboBox.setSelectedItem(prefs.getKeyOrdering());
            useNativeTypesCheckBox.setSelected(prefs.isUseNativeTypes());
            omitXsdStringTypesCheckBox.setSelected(prefs.isOmitXsdStringTypes());
            useRdfTypeCheckBox.setSelected(prefs.isUseRdfType());
            prettyPrintCheckBox.setSelected(prefs.isPrettyPrint());
            indentSpacesSpinner.setValue(prefs.getIndentSpaces());

            // Group 4
            blankNodeLabelingComboBox.setSelectedItem(prefs.getBlankNodeLabeling());
            blankNodeEmbeddingComboBox.setSelectedItem(prefs.getBlankNodeEmbedding());
            rdfListHandlingComboBox.setSelectedItem(prefs.getRdfListHandling());
            listPropertyOverridesField.setText(prefs.getListPropertyOverrides());
            useLanguageMapsCheckBox.setSelected(prefs.isUseLanguageMaps());
            nullLanguageForUntaggedCheckBox.setSelected(prefs.isNullLanguageForUntagged());

            updateEnabledStates();
        } finally {
            isUpdatingUI = false;
        }
    }

    private void updateEnabledStates() {
        boolean hasContext = contextEmbeddingComboBox.getSelectedItem() != ContextEmbedding.NONE;
        contextSourceComboBox.setEnabled(hasContext);
        includeOntologyPrefixesCheckBox.setEnabled(hasContext);
        pruneUnusedPrefixesCheckBox.setEnabled(hasContext);
        useVocabCheckBox.setEnabled(hasContext);

        boolean hasTerms = generateTermsForComboBox.getSelectedItem() != GenerateTermsFor.NONE && hasContext;
        termNameSourceComboBox.setEnabled(hasTerms);
        termCollisionPolicyComboBox.setEnabled(hasTerms);

        boolean isExpanded = documentFormComboBox.getSelectedItem() == DocumentForm.EXPANDED;
        compactArraysCheckBox.setEnabled(!isExpanded);
        wrapInGraphArrayCheckBox.setEnabled(!isExpanded);

        boolean isPretty = prettyPrintCheckBox.isSelected();
        indentSpacesSpinner.setEnabled(isPretty);

        boolean isLangMaps = useLanguageMapsCheckBox.isSelected();
        nullLanguageForUntaggedCheckBox.setEnabled(isLangMaps);

        boolean isListContainer = rdfListHandlingComboBox.getSelectedItem() == RdfListHandling.LIST_CONTAINER;
        listPropertyOverridesField.setEnabled(isListContainer);
    }

    @Override
    public void applyChanges() {
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();

        prefs.setActivePreset((JsonLdPreset) presetComboBox.getSelectedItem());

        // Group 1
        prefs.setContextEmbedding((ContextEmbedding) contextEmbeddingComboBox.getSelectedItem());
        prefs.setContextSource((ContextSource) contextSourceComboBox.getSelectedItem());
        prefs.setIncludeOntologyPrefixes(includeOntologyPrefixesCheckBox.isSelected());
        prefs.setPruneUnusedPrefixes(pruneUnusedPrefixesCheckBox.isSelected());
        prefs.setUseVocab(useVocabCheckBox.isSelected());
        prefs.setIncludeImportsClosure(includeImportsClosureCheckBox.isSelected());

        // Group 2
        prefs.setGenerateTermsFor((GenerateTermsFor) generateTermsForComboBox.getSelectedItem());
        prefs.setTermNameSource((TermNameSource) termNameSourceComboBox.getSelectedItem());
        prefs.setTermCollisionPolicy((TermCollisionPolicy) termCollisionPolicyComboBox.getSelectedItem());
        prefs.setTypeCoercionMode((TypeCoercionMode) typeCoercionModeComboBox.getSelectedItem());

        // Group 3
        prefs.setDocumentForm((DocumentForm) documentFormComboBox.getSelectedItem());
        prefs.setWrapInGraphArray(wrapInGraphArrayCheckBox.isSelected());
        prefs.setCompactArrays(compactArraysCheckBox.isSelected());
        prefs.setKeyOrdering((KeyOrdering) keyOrderingComboBox.getSelectedItem());
        prefs.setUseNativeTypes(useNativeTypesCheckBox.isSelected());
        prefs.setOmitXsdStringTypes(omitXsdStringTypesCheckBox.isSelected());
        prefs.setUseRdfType(useRdfTypeCheckBox.isSelected());
        prefs.setPrettyPrint(prettyPrintCheckBox.isSelected());
        prefs.setIndentSpaces((Integer) indentSpacesSpinner.getValue());

        // Group 4
        prefs.setBlankNodeLabeling((BlankNodeLabeling) blankNodeLabelingComboBox.getSelectedItem());
        prefs.setBlankNodeEmbedding((BlankNodeEmbedding) blankNodeEmbeddingComboBox.getSelectedItem());
        prefs.setRdfListHandling((RdfListHandling) rdfListHandlingComboBox.getSelectedItem());
        prefs.setListPropertyOverrides(listPropertyOverridesField.getText().trim());
        prefs.setUseLanguageMaps(useLanguageMapsCheckBox.isSelected());
        prefs.setNullLanguageForUntagged(nullLanguageForUntaggedCheckBox.isSelected());

        prefs.save();
    }

    @Override
    public void dispose() {
    }
}
