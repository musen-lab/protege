package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.owl.model.io.jsonld.*;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Objects;

/**
 * The JSON-LD tab in the Preferences window. Shows presets and settings for context generation,
 * document shape, literal handling, and scope. Controls edit a local UI state until
 * {@link #applyChanges()} copies their values to {@link JsonLdPreferences} and persists them.
 */
public class JsonLdPreferencesPanel extends OWLPreferencesPanel {

    private static final long serialVersionUID = 1L;

    private final JsonLdPreferences prefs;

    // Preset & Actions
    private JComboBox<JsonLdPreset> presetComboBox;
    private JButton resetButton;

    // Context & Prefixes
    private JCheckBox includeOntologyPrefixesCheckBox;
    private JCheckBox pruneUnusedPrefixesCheckBox;
    private JCheckBox useVocabCheckBox;
    private JComboBox<GenerateTermsFor> generateTermsForComboBox;
    private JComboBox<TermNameSource> termNameSourceComboBox;
    private JComboBox<TermCollisionPolicy> termCollisionPolicyComboBox;

    // Output Shape & Formatting
    private JComboBox<DocumentForm> documentFormComboBox;
    private JCheckBox compactArraysCheckBox;
    private JComboBox<KeyOrdering> keyOrderingComboBox;
    private JCheckBox prettyPrintCheckBox;
    private JSpinner indentSpacesSpinner;

    // Literals & Types
    private JCheckBox useNativeTypesCheckBox;
    private JComboBox<TypeCoercionMode> typeCoercionModeComboBox;
    private JCheckBox useRdfTypeCheckBox;
    private JCheckBox useLanguageMapsCheckBox;

    // Structures & Scope
    private JComboBox<BlankNodeEmbedding> blankNodeEmbeddingComboBox;
    private JCheckBox includeImportsClosureCheckBox;

    private boolean isUpdatingUI = false;

    /** Creates the panel for Protégé's application-wide JSON-LD settings. */
    public JsonLdPreferencesPanel() {
        this(JsonLdPreferences.getInstance());
    }

    /** Creates a panel for a supplied settings instance, primarily for UI tests. */
    JsonLdPreferencesPanel(@Nonnull JsonLdPreferences prefs) {
        this.prefs = Objects.requireNonNull(prefs);
    }

    @Override
    public void initialise() throws Exception {
        removeAll();
        createUI();
        updateUIFromPreferences(prefs);
        attachListeners();
    }

    private void createUI() {
        setLayout(new BorderLayout());

        PreferencesLayoutPanel panel = new PreferencesLayoutPanel();

        // --- Preset ---
        panel.addGroup("Preset");
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        presetComboBox = new JComboBox<>(JsonLdPreset.values());
        resetButton = new JButton("Reset to defaults");
        presetPanel.add(presetComboBox);
        presetPanel.add(resetButton);
        panel.addGroupComponent(presetPanel);

        // --- Context & Prefixes ---
        panel.addSeparator();
        panel.addGroup("Context & Prefixes");
        includeOntologyPrefixesCheckBox = new JCheckBox("Include the ontology's prefixes in @context");
        pruneUnusedPrefixesCheckBox = new JCheckBox("Leave out prefixes that nothing in the file uses");
        useVocabCheckBox = new JCheckBox("Add @vocab (the ontology IRI)");
        generateTermsForComboBox = new JComboBox<>(GenerateTermsFor.values());
        termNameSourceComboBox = new JComboBox<>(TermNameSource.values());
        termCollisionPolicyComboBox = new JComboBox<>(TermCollisionPolicy.values());

        panel.addGroupComponent(includeOntologyPrefixesCheckBox);
        panel.addGroupComponent(pruneUnusedPrefixesCheckBox);
        panel.addGroupComponent(useVocabCheckBox);
        panel.addGroupComponent(new JLabel("Short names for:"));
        panel.addIndentedGroupComponent(generateTermsForComboBox);
        panel.addGroupComponent(new JLabel("Take short names from:"));
        panel.addIndentedGroupComponent(termNameSourceComboBox);
        panel.addGroupComponent(new JLabel("When two entities want the same short name:"));
        panel.addIndentedGroupComponent(termCollisionPolicyComboBox);

        // --- Output Shape & Formatting ---
        panel.addSeparator();
        panel.addGroup("Output Shape & Formatting");
        documentFormComboBox = new JComboBox<>(DocumentForm.values());
        compactArraysCheckBox = new JCheckBox("Write a single value without [ ]");
        keyOrderingComboBox = new JComboBox<>(KeyOrdering.values());
        prettyPrintCheckBox = new JCheckBox("Pretty print (indented and readable)");
        indentSpacesSpinner = new JSpinner(new SpinnerNumberModel(JsonLdPreferences.DEFAULT_INDENT_SPACES,
                                                                  JsonLdPreferences.MIN_INDENT_SPACES,
                                                                  JsonLdPreferences.MAX_INDENT_SPACES,
                                                                  1));

        JPanel indentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        indentPanel.add(new JLabel("Spaces per indent level:"));
        indentPanel.add(indentSpacesSpinner);

        panel.addGroupComponent(new JLabel("File shape:"));
        panel.addIndentedGroupComponent(documentFormComboBox);
        panel.addGroupComponent(compactArraysCheckBox);
        panel.addGroupComponent(new JLabel("Key ordering:"));
        panel.addIndentedGroupComponent(keyOrderingComboBox);
        panel.addGroupComponent(prettyPrintCheckBox);
        panel.addIndentedGroupComponent(indentPanel);

        // --- Literals & Types ---
        panel.addSeparator();
        panel.addGroup("Literals & Types");
        useNativeTypesCheckBox = new JCheckBox("Write numbers and true/false as plain JSON values");
        typeCoercionModeComboBox = new JComboBox<>(TypeCoercionMode.values());
        useRdfTypeCheckBox = new JCheckBox("Write rdf:type as a normal property (instead of @type)");
        useLanguageMapsCheckBox = new JCheckBox("Group text by language (language maps)");

        panel.addGroupComponent(useNativeTypesCheckBox);
        panel.addGroupComponent(new JLabel("Links as plain IRIs (\"@type\": \"@id\"):"));
        panel.addIndentedGroupComponent(typeCoercionModeComboBox);
        panel.addGroupComponent(useRdfTypeCheckBox);
        panel.addGroupComponent(useLanguageMapsCheckBox);

        // --- Structures & Scope ---
        panel.addSeparator();
        panel.addGroup("Structures & Scope");
        blankNodeEmbeddingComboBox = new JComboBox<>(BlankNodeEmbedding.values());
        includeImportsClosureCheckBox = new JCheckBox("Also write imported ontologies into the file");

        panel.addGroupComponent(new JLabel("Blank nodes (for example restrictions):"));
        panel.addIndentedGroupComponent(blankNodeEmbeddingComboBox);
        panel.addGroupComponent(includeImportsClosureCheckBox);

        nameControls();

        add(panel, BorderLayout.NORTH);
    }

    /** Names each control after its setting key, so tests can find them. */
    private void nameControls() {
        presetComboBox.setName(JsonLdPreferences.KEY_ACTIVE_PRESET);
        resetButton.setName("reset");
        includeOntologyPrefixesCheckBox.setName(JsonLdPreferences.KEY_INCLUDE_ONTOLOGY_PREFIXES);
        pruneUnusedPrefixesCheckBox.setName(JsonLdPreferences.KEY_PRUNE_UNUSED_PREFIXES);
        useVocabCheckBox.setName(JsonLdPreferences.KEY_USE_VOCAB);
        generateTermsForComboBox.setName(JsonLdPreferences.KEY_GENERATE_TERMS_FOR);
        termNameSourceComboBox.setName(JsonLdPreferences.KEY_TERM_NAME_SOURCE);
        termCollisionPolicyComboBox.setName(JsonLdPreferences.KEY_TERM_COLLISION_POLICY);
        documentFormComboBox.setName(JsonLdPreferences.KEY_DOCUMENT_FORM);
        compactArraysCheckBox.setName(JsonLdPreferences.KEY_COMPACT_ARRAYS);
        keyOrderingComboBox.setName(JsonLdPreferences.KEY_KEY_ORDERING);
        prettyPrintCheckBox.setName(JsonLdPreferences.KEY_PRETTY_PRINT);
        indentSpacesSpinner.setName(JsonLdPreferences.KEY_INDENT_SPACES);
        useNativeTypesCheckBox.setName(JsonLdPreferences.KEY_USE_NATIVE_TYPES);
        typeCoercionModeComboBox.setName(JsonLdPreferences.KEY_TYPE_COERCION_MODE);
        useRdfTypeCheckBox.setName(JsonLdPreferences.KEY_USE_RDF_TYPE);
        useLanguageMapsCheckBox.setName(JsonLdPreferences.KEY_USE_LANGUAGE_MAPS);
        blankNodeEmbeddingComboBox.setName(JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING);
        includeImportsClosureCheckBox.setName(JsonLdPreferences.KEY_INCLUDE_IMPORTS_CLOSURE);
    }

    private void attachListeners() {
        presetComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !isUpdatingUI) {
                JsonLdPreset selected = (JsonLdPreset) presetComboBox.getSelectedItem();
                if (selected != null && selected != JsonLdPreset.CUSTOM) {
                    // Only the screen changes; the settings change when the user clicks Apply or OK
                    updateUIFromPreferences(JsonLdPreferences.ofPreset(selected));
                }
            }
        });

        resetButton.addActionListener(e -> updateUIFromPreferences(JsonLdPreferences.ofPreset(JsonLdPreferences.DEFAULT_ACTIVE_PRESET)));

        // Modification listener for all individual components
        Runnable onModified = () -> {
            if (!isUpdatingUI) {
                presetComboBox.setSelectedItem(JsonLdPreset.CUSTOM);
                updateEnabledStates();
            }
        };

        // Combos
        generateTermsForComboBox.addItemListener(e -> onModified.run());
        termNameSourceComboBox.addItemListener(e -> onModified.run());
        termCollisionPolicyComboBox.addItemListener(e -> onModified.run());
        documentFormComboBox.addItemListener(e -> onModified.run());
        keyOrderingComboBox.addItemListener(e -> onModified.run());
        typeCoercionModeComboBox.addItemListener(e -> onModified.run());
        blankNodeEmbeddingComboBox.addItemListener(e -> onModified.run());

        // Checkboxes
        includeOntologyPrefixesCheckBox.addActionListener(e -> onModified.run());
        pruneUnusedPrefixesCheckBox.addActionListener(e -> onModified.run());
        useVocabCheckBox.addActionListener(e -> onModified.run());
        compactArraysCheckBox.addActionListener(e -> onModified.run());
        prettyPrintCheckBox.addActionListener(e -> onModified.run());
        useNativeTypesCheckBox.addActionListener(e -> onModified.run());
        useRdfTypeCheckBox.addActionListener(e -> onModified.run());
        useLanguageMapsCheckBox.addActionListener(e -> onModified.run());
        includeImportsClosureCheckBox.addActionListener(e -> onModified.run());

        // Spinner
        indentSpacesSpinner.addChangeListener(e -> onModified.run());
    }

    private void updateUIFromPreferences(@Nonnull JsonLdPreferences source) {
        isUpdatingUI = true;
        try {
            presetComboBox.setSelectedItem(source.getActivePreset());

            // Context & Prefixes
            includeOntologyPrefixesCheckBox.setSelected(source.isIncludeOntologyPrefixes());
            pruneUnusedPrefixesCheckBox.setSelected(source.isPruneUnusedPrefixes());
            useVocabCheckBox.setSelected(source.isUseVocab());
            generateTermsForComboBox.setSelectedItem(source.getGenerateTermsFor());
            termNameSourceComboBox.setSelectedItem(source.getTermNameSource());
            termCollisionPolicyComboBox.setSelectedItem(source.getTermCollisionPolicy());

            // Output Shape & Formatting
            documentFormComboBox.setSelectedItem(source.getDocumentForm());
            compactArraysCheckBox.setSelected(source.isCompactArrays());
            keyOrderingComboBox.setSelectedItem(source.getKeyOrdering());
            prettyPrintCheckBox.setSelected(source.isPrettyPrint());
            indentSpacesSpinner.setValue(source.getIndentSpaces());

            // Literals & Types
            useNativeTypesCheckBox.setSelected(source.isUseNativeTypes());
            typeCoercionModeComboBox.setSelectedItem(source.getTypeCoercionMode());
            useRdfTypeCheckBox.setSelected(source.isUseRdfType());
            useLanguageMapsCheckBox.setSelected(source.isUseLanguageMaps());

            // Structures & Scope
            blankNodeEmbeddingComboBox.setSelectedItem(source.getBlankNodeEmbedding());
            includeImportsClosureCheckBox.setSelected(source.isIncludeImportsClosure());

            updateEnabledStates();
        } finally {
            isUpdatingUI = false;
        }
    }

    private void updateEnabledStates() {
        DocumentForm form = (DocumentForm) documentFormComboBox.getSelectedItem();
        // Expanded files have no @context, so nothing that goes into the context has an effect
        boolean hasContext = form != DocumentForm.EXPANDED;
        boolean hasTerms = hasContext && generateTermsForComboBox.getSelectedItem() != GenerateTermsFor.NONE;

        includeOntologyPrefixesCheckBox.setEnabled(hasContext);
        pruneUnusedPrefixesCheckBox.setEnabled(hasContext);
        useVocabCheckBox.setEnabled(hasContext);
        generateTermsForComboBox.setEnabled(hasContext);
        termNameSourceComboBox.setEnabled(hasTerms);
        termCollisionPolicyComboBox.setEnabled(hasTerms);
        compactArraysCheckBox.setEnabled(hasContext);
        typeCoercionModeComboBox.setEnabled(hasContext);
        useLanguageMapsCheckBox.setEnabled(hasContext);

        indentSpacesSpinner.setEnabled(prettyPrintCheckBox.isSelected());
        // Flattened files are never nested
        blankNodeEmbeddingComboBox.setEnabled(form != DocumentForm.FLATTENED);
    }

    @Override
    public void applyChanges() {
        prefs.setActivePreset((JsonLdPreset) presetComboBox.getSelectedItem());

        // Context & Prefixes
        prefs.setIncludeOntologyPrefixes(includeOntologyPrefixesCheckBox.isSelected());
        prefs.setPruneUnusedPrefixes(pruneUnusedPrefixesCheckBox.isSelected());
        prefs.setUseVocab(useVocabCheckBox.isSelected());
        prefs.setGenerateTermsFor((GenerateTermsFor) generateTermsForComboBox.getSelectedItem());
        prefs.setTermNameSource((TermNameSource) termNameSourceComboBox.getSelectedItem());
        prefs.setTermCollisionPolicy((TermCollisionPolicy) termCollisionPolicyComboBox.getSelectedItem());

        // Output Shape & Formatting
        prefs.setDocumentForm((DocumentForm) documentFormComboBox.getSelectedItem());
        prefs.setCompactArrays(compactArraysCheckBox.isSelected());
        prefs.setKeyOrdering((KeyOrdering) keyOrderingComboBox.getSelectedItem());
        prefs.setPrettyPrint(prettyPrintCheckBox.isSelected());
        prefs.setIndentSpaces((Integer) indentSpacesSpinner.getValue());

        // Literals & Types
        prefs.setUseNativeTypes(useNativeTypesCheckBox.isSelected());
        prefs.setTypeCoercionMode((TypeCoercionMode) typeCoercionModeComboBox.getSelectedItem());
        prefs.setUseRdfType(useRdfTypeCheckBox.isSelected());
        prefs.setUseLanguageMaps(useLanguageMapsCheckBox.isSelected());

        // Structures & Scope
        prefs.setBlankNodeEmbedding((BlankNodeEmbedding) blankNodeEmbeddingComboBox.getSelectedItem());
        prefs.setIncludeImportsClosure(includeImportsClosureCheckBox.isSelected());

        prefs.save();
    }

    @Override
    public void dispose() {
    }
}
