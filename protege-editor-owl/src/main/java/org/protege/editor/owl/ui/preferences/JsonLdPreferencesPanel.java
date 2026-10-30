package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.owl.model.io.jsonld.*;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The JSON-LD tab in the Preferences window. Shows presets and settings for context generation,
 * document shape, literal handling, and blank nodes. Controls edit a local UI state until
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
    private EnumRadioButtons<GenerateTermsFor> generateTermsForButtons;
    private EnumRadioButtons<TermNameSource> termNameSourceButtons;
    private EnumRadioButtons<TermCollisionPolicy> termCollisionPolicyButtons;

    // Output Shape & Formatting
    private EnumRadioButtons<DocumentForm> documentFormButtons;
    private JCheckBox compactArraysCheckBox;
    private EnumRadioButtons<KeyOrdering> keyOrderingButtons;
    private JCheckBox prettyPrintCheckBox;
    private JSpinner indentSpacesSpinner;

    // Literals & Types
    private JCheckBox useNativeTypesCheckBox;
    private EnumRadioButtons<TypeCoercionMode> typeCoercionModeButtons;
    private JCheckBox useRdfTypeCheckBox;
    private JCheckBox useLanguageMapsCheckBox;

    // Structures
    private EnumRadioButtons<BlankNodeEmbedding> blankNodeEmbeddingButtons;

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
        useVocabCheckBox = new JCheckBox("Use the default prefix for @vocab and relative IRIs");
        generateTermsForButtons = new EnumRadioButtons<>(GenerateTermsFor.values());
        termNameSourceButtons = new EnumRadioButtons<>(TermNameSource.values());
        termCollisionPolicyButtons = new EnumRadioButtons<>(TermCollisionPolicy.values());

        panel.addGroupComponent(includeOntologyPrefixesCheckBox);
        panel.addGroupComponent(pruneUnusedPrefixesCheckBox);
        panel.addGroupComponent(useVocabCheckBox);
        addChoiceGroup(panel, "Short names for", generateTermsForButtons);
        addChoiceGroup(panel, "Short name source", termNameSourceButtons);
        addChoiceGroup(panel, "Name collisions", termCollisionPolicyButtons);

        // --- Output Shape & Formatting ---
        panel.addSeparator();
        documentFormButtons = new EnumRadioButtons<>(DocumentForm.values());
        compactArraysCheckBox = new JCheckBox("Write a single value without [ ]");
        keyOrderingButtons = new EnumRadioButtons<>(KeyOrdering.values());
        prettyPrintCheckBox = new JCheckBox("Pretty print (indented and readable)");
        indentSpacesSpinner = new JSpinner(new SpinnerNumberModel(JsonLdPreferences.DEFAULT_INDENT_SPACES,
                                                                  JsonLdPreferences.MIN_INDENT_SPACES,
                                                                  JsonLdPreferences.MAX_INDENT_SPACES,
                                                                  1));

        JPanel indentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        indentPanel.add(new JLabel("Spaces per indent level:"));
        indentPanel.add(indentSpacesSpinner);

        addChoiceGroup(panel, "File shape", documentFormButtons);
        panel.addGroupComponent(compactArraysCheckBox);
        addChoiceGroup(panel, "Key ordering", keyOrderingButtons);
        panel.addGroupComponent(prettyPrintCheckBox);
        panel.addIndentedGroupComponent(indentPanel);

        // --- Literals & Types ---
        panel.addSeparator();
        panel.addGroup("Literals & Types");
        useNativeTypesCheckBox = new JCheckBox("Write numbers and true/false as plain JSON values");
        typeCoercionModeButtons = new EnumRadioButtons<>(TypeCoercionMode.values());
        useRdfTypeCheckBox = new JCheckBox("Write rdf:type as a normal property (instead of @type)");
        useLanguageMapsCheckBox = new JCheckBox("Group text by language (language maps)");

        panel.addGroupComponent(useNativeTypesCheckBox);
        addChoiceGroup(panel, "IRI-valued links", typeCoercionModeButtons);
        panel.addGroupComponent(useRdfTypeCheckBox);
        panel.addGroupComponent(useLanguageMapsCheckBox);

        // --- Structures ---
        panel.addSeparator();
        blankNodeEmbeddingButtons = new EnumRadioButtons<>(BlankNodeEmbedding.values());

        addChoiceGroup(panel, "Blank nodes", blankNodeEmbeddingButtons);

        nameControls();

        add(panel, BorderLayout.NORTH);
    }

    private static void addChoiceGroup(PreferencesLayoutPanel panel, String label, EnumRadioButtons<?> choices) {
        panel.addVerticalPadding();
        panel.addGroup(label);
        choices.getAccessibleContext().setAccessibleName(label);
        panel.addGroupComponent(choices);
    }

    /** Keeps each enum's choices exclusive and enables or disables every button together. */
    private static final class EnumRadioButtons<E extends Enum<E>> extends JPanel {

        private final Map<E, JRadioButton> buttons = new LinkedHashMap<>();

        EnumRadioButtons(E[] values) {
            setLayout(new GridLayout(0, 1));
            ButtonGroup group = new ButtonGroup();
            for (E value : values) {
                JRadioButton button = new JRadioButton(value.toString());
                button.setActionCommand(value.name());
                group.add(button);
                buttons.put(value, button);
                add(button);
            }
        }

        E getSelectedValue() {
            return buttons.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey).findFirst().orElseThrow(IllegalStateException::new);
        }

        void setSelectedValue(E value) {
            buttons.get(value).setSelected(true);
        }

        void onSelection(Runnable listener) {
            buttons.values().forEach(button -> button.addActionListener(event -> listener.run()));
        }

        @Override
        public int getBaseline(int width, int height) {
            // Align the group label with the first choice, rather than the middle of the group.
            return getComponent(0).getBaseline(width, height / getComponentCount());
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (Component button : getComponents()) {
                button.setEnabled(enabled);
            }
        }
    }

    /** Names each control after its setting key, so tests can find them. */
    private void nameControls() {
        presetComboBox.setName(JsonLdPreferences.KEY_ACTIVE_PRESET);
        resetButton.setName("reset");
        includeOntologyPrefixesCheckBox.setName(JsonLdPreferences.KEY_INCLUDE_ONTOLOGY_PREFIXES);
        pruneUnusedPrefixesCheckBox.setName(JsonLdPreferences.KEY_PRUNE_UNUSED_PREFIXES);
        useVocabCheckBox.setName(JsonLdPreferences.KEY_USE_VOCAB);
        generateTermsForButtons.setName(JsonLdPreferences.KEY_GENERATE_TERMS_FOR);
        termNameSourceButtons.setName(JsonLdPreferences.KEY_TERM_NAME_SOURCE);
        termCollisionPolicyButtons.setName(JsonLdPreferences.KEY_TERM_COLLISION_POLICY);
        documentFormButtons.setName(JsonLdPreferences.KEY_DOCUMENT_FORM);
        compactArraysCheckBox.setName(JsonLdPreferences.KEY_COMPACT_ARRAYS);
        keyOrderingButtons.setName(JsonLdPreferences.KEY_KEY_ORDERING);
        prettyPrintCheckBox.setName(JsonLdPreferences.KEY_PRETTY_PRINT);
        indentSpacesSpinner.setName(JsonLdPreferences.KEY_INDENT_SPACES);
        useNativeTypesCheckBox.setName(JsonLdPreferences.KEY_USE_NATIVE_TYPES);
        typeCoercionModeButtons.setName(JsonLdPreferences.KEY_TYPE_COERCION_MODE);
        useRdfTypeCheckBox.setName(JsonLdPreferences.KEY_USE_RDF_TYPE);
        useLanguageMapsCheckBox.setName(JsonLdPreferences.KEY_USE_LANGUAGE_MAPS);
        blankNodeEmbeddingButtons.setName(JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING);
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

        // Radio-button groups
        generateTermsForButtons.onSelection(onModified);
        termNameSourceButtons.onSelection(onModified);
        termCollisionPolicyButtons.onSelection(onModified);
        documentFormButtons.onSelection(onModified);
        keyOrderingButtons.onSelection(onModified);
        typeCoercionModeButtons.onSelection(onModified);
        blankNodeEmbeddingButtons.onSelection(onModified);

        // Checkboxes
        includeOntologyPrefixesCheckBox.addActionListener(e -> onModified.run());
        pruneUnusedPrefixesCheckBox.addActionListener(e -> onModified.run());
        useVocabCheckBox.addActionListener(e -> onModified.run());
        compactArraysCheckBox.addActionListener(e -> onModified.run());
        prettyPrintCheckBox.addActionListener(e -> onModified.run());
        useNativeTypesCheckBox.addActionListener(e -> onModified.run());
        useRdfTypeCheckBox.addActionListener(e -> onModified.run());
        useLanguageMapsCheckBox.addActionListener(e -> onModified.run());

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
            generateTermsForButtons.setSelectedValue(source.getGenerateTermsFor());
            termNameSourceButtons.setSelectedValue(source.getTermNameSource());
            termCollisionPolicyButtons.setSelectedValue(source.getTermCollisionPolicy());

            // Output Shape & Formatting
            documentFormButtons.setSelectedValue(source.getDocumentForm());
            compactArraysCheckBox.setSelected(source.isCompactArrays());
            keyOrderingButtons.setSelectedValue(source.getKeyOrdering());
            prettyPrintCheckBox.setSelected(source.isPrettyPrint());
            indentSpacesSpinner.setValue(source.getIndentSpaces());

            // Literals & Types
            useNativeTypesCheckBox.setSelected(source.isUseNativeTypes());
            typeCoercionModeButtons.setSelectedValue(source.getTypeCoercionMode());
            useRdfTypeCheckBox.setSelected(source.isUseRdfType());
            useLanguageMapsCheckBox.setSelected(source.isUseLanguageMaps());

            // Structures
            blankNodeEmbeddingButtons.setSelectedValue(source.getBlankNodeEmbedding());

            updateEnabledStates();
        } finally {
            isUpdatingUI = false;
        }
    }

    private void updateEnabledStates() {
        DocumentForm form = documentFormButtons.getSelectedValue();
        // Expanded files have no @context, so nothing that goes into the context has an effect
        boolean hasContext = form != DocumentForm.EXPANDED;
        boolean hasTerms = hasContext && generateTermsForButtons.getSelectedValue() != GenerateTermsFor.NONE;

        includeOntologyPrefixesCheckBox.setEnabled(hasContext);
        pruneUnusedPrefixesCheckBox.setEnabled(hasContext);
        useVocabCheckBox.setEnabled(hasContext);
        generateTermsForButtons.setEnabled(hasContext);
        termNameSourceButtons.setEnabled(hasTerms);
        termCollisionPolicyButtons.setEnabled(hasTerms);
        compactArraysCheckBox.setEnabled(hasContext);
        typeCoercionModeButtons.setEnabled(hasContext);
        useLanguageMapsCheckBox.setEnabled(hasContext);

        indentSpacesSpinner.setEnabled(prettyPrintCheckBox.isSelected());
        // Flattened files are never nested
        blankNodeEmbeddingButtons.setEnabled(form != DocumentForm.FLATTENED);
    }

    @Override
    public void applyChanges() {
        prefs.setActivePreset((JsonLdPreset) presetComboBox.getSelectedItem());

        // Context & Prefixes
        prefs.setIncludeOntologyPrefixes(includeOntologyPrefixesCheckBox.isSelected());
        prefs.setPruneUnusedPrefixes(pruneUnusedPrefixesCheckBox.isSelected());
        prefs.setUseVocab(useVocabCheckBox.isSelected());
        prefs.setGenerateTermsFor(generateTermsForButtons.getSelectedValue());
        prefs.setTermNameSource(termNameSourceButtons.getSelectedValue());
        prefs.setTermCollisionPolicy(termCollisionPolicyButtons.getSelectedValue());

        // Output Shape & Formatting
        prefs.setDocumentForm(documentFormButtons.getSelectedValue());
        prefs.setCompactArrays(compactArraysCheckBox.isSelected());
        prefs.setKeyOrdering(keyOrderingButtons.getSelectedValue());
        prefs.setPrettyPrint(prettyPrintCheckBox.isSelected());
        prefs.setIndentSpaces((Integer) indentSpacesSpinner.getValue());

        // Literals & Types
        prefs.setUseNativeTypes(useNativeTypesCheckBox.isSelected());
        prefs.setTypeCoercionMode(typeCoercionModeButtons.getSelectedValue());
        prefs.setUseRdfType(useRdfTypeCheckBox.isSelected());
        prefs.setUseLanguageMaps(useLanguageMapsCheckBox.isSelected());

        // Structures
        prefs.setBlankNodeEmbedding(blankNodeEmbeddingButtons.getSelectedValue());

        prefs.save();
    }

    @Override
    public void dispose() {
    }
}
