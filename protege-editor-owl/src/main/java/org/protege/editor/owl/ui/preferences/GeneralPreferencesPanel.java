package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.core.ui.view.View;
import org.protege.editor.owl.model.axiom.FreshAxiomLocation;
import org.protege.editor.owl.model.axiom.FreshAxiomLocationPreferences;
import org.protege.editor.owl.model.declaration.DeclarationSynthesisPreferences;
import org.protege.editor.owl.model.search.SearchManagePluginListCellRenderer;
import org.protege.editor.owl.model.search.SearchManagerPlugin;
import org.protege.editor.owl.model.search.SearchManagerSelector;
import org.protege.editor.owl.ui.clsdescriptioneditor.ExpressionEditorPreferences;
import org.protege.editor.owl.ui.tree.OWLTreePreferences;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: 03-Sep-2006<br><br>

 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class GeneralPreferencesPanel extends OWLPreferencesPanel {

    public static final String DIALOGS_ALWAYS_CENTRED = "DIALOGS_ALWAYS_CENTRED";
    
    private JSpinner checkDelaySpinner;

    private static final String SECOND_TOOL_TIP = "1000 = 1 second";

    private static final String KEEP_DECLARATIONS_LABEL =
            "Keep entity declarations in the ontology that defines the entity";

    private static final String KEEP_DECLARATIONS_TOOL_TIP =
            "<html>For modular ontologies, where a term should be declared by the module that owns "
                    + "it.<br><br>"
                    + "When this is ticked, saving to RDF/XML or Turtle writes a declaration only for "
                    + "an entity this ontology declares itself.  An entity it merely refers to is left "
                    + "to the ontology that owns it, instead of being claimed here.<br>"
                    + "A declaration the ontology states explicitly is always written, even when an "
                    + "import carries it too.<br><br>"
                    + "This matters most when editing a module whose imports are not loaded, where "
                    + "every borrowed term otherwise looks undeclared.<br><br>"
                    + "The OWL API also adds a comment to files saved this way, noting that type "
                    + "declarations were not added automatically.</html>";

    private JCheckBox alwaysCentreDialogsCheckbox;

    private JCheckBox detachedWindowsFloat;

    private JRadioButton addFreshAxiomsToActiveOntologyRadioButton;

    private JRadioButton addFreshAxiomsToSubjectDefiningOntology;

    private JCheckBox keepDeclarationsInDefiningOntologyCheckBox;


    private JCheckBox autoExpandEnabledCheckBox;

    private JCheckBox paintLinesCheckBox;

    private JSpinner autoExpandMaxDepthSpinner;

    private JSpinner autoExpandMaxChildSizeSpinner;

    private final JCheckBox dragAndDropEnabled = new JCheckBox("Allow drag and drop in trees");


    private JComboBox<SearchManagerPlugin> searchManagerPluginComboBox = new JComboBox<>();


    public void applyChanges() {
        ExpressionEditorPreferences.getInstance().setCheckDelay((Integer) checkDelaySpinner.getModel().getValue());

        Preferences appPrefs = PreferencesManager.getInstance().getApplicationPreferences(ProtegeApplication.ID);
        appPrefs.putBoolean(DIALOGS_ALWAYS_CENTRED, alwaysCentreDialogsCheckbox.isSelected());
        appPrefs.putBoolean(View.DETACHED_WINDOWS_FLOAT, detachedWindowsFloat.isSelected());

        FreshAxiomLocationPreferences axiomPrefs = FreshAxiomLocationPreferences.getPreferences();
        if(addFreshAxiomsToActiveOntologyRadioButton.isSelected()) {
            axiomPrefs.setFreshAxiomLocation(FreshAxiomLocation.ACTIVE_ONTOLOGY);
        }
        else if (addFreshAxiomsToSubjectDefiningOntology.isSelected()) {
            axiomPrefs.setFreshAxiomLocation(FreshAxiomLocation.SUBJECT_DEFINING_ONTOLOGY);
        }

        DeclarationSynthesisPreferences.getPreferences().setKeepDeclarationsInDefiningOntology(
                keepDeclarationsInDefiningOntologyCheckBox.isSelected());

        OWLTreePreferences prefs = OWLTreePreferences.getInstance();
        prefs.setAutoExpansionEnabled(autoExpandEnabledCheckBox.isSelected());
        prefs.setPaintLines(paintLinesCheckBox.isSelected());
        prefs.setAutoExpansionDepthLimit((Integer)autoExpandMaxDepthSpinner.getValue());
        prefs.setAutoExpansionChildLimit((Integer) autoExpandMaxChildSizeSpinner.getValue());
        prefs.setTreeDragAndDropEnabled(dragAndDropEnabled.isSelected());
        SearchManagerPlugin plugin = (SearchManagerPlugin) searchManagerPluginComboBox.getSelectedItem();
        if(plugin != null) {
            getOWLEditorKit().getSearchManagerSelector().setCurrentPluginId(plugin.getId());
        }
    }

    private void createUI() {
        setLayout(new BorderLayout());
        PreferencesLayoutPanel panel = new PreferencesLayoutPanel();
        add(panel, BorderLayout.NORTH);
        panel.addGroup("Editor delay");
        final int checkDelay = ExpressionEditorPreferences.getInstance().getCheckDelay();
        checkDelaySpinner = new JSpinner(new SpinnerNumberModel(checkDelay, 0, 10000, 50));
        checkDelaySpinner.setToolTipText(SECOND_TOOL_TIP);
        panel.addGroupComponent(checkDelaySpinner);

        Preferences appPrefs = PreferencesManager.getInstance().getApplicationPreferences(ProtegeApplication.ID);
        alwaysCentreDialogsCheckbox = new JCheckBox("Centre dialogs on workspace");
        alwaysCentreDialogsCheckbox.setSelected(appPrefs.getBoolean(DIALOGS_ALWAYS_CENTRED, false));
        detachedWindowsFloat = new JCheckBox("Detached windows float");
        detachedWindowsFloat.setSelected(appPrefs.getBoolean(View.DETACHED_WINDOWS_FLOAT, true));

        panel.addSeparator();
        panel.addGroup("Windows");
        panel.addGroupComponent(alwaysCentreDialogsCheckbox);
        panel.addGroupComponent(detachedWindowsFloat);

        panel.addSeparator();
        ButtonGroup axiomButtonGroup = new ButtonGroup();
        addFreshAxiomsToActiveOntologyRadioButton = new JRadioButton("Add fresh axioms to active ontology",
                FreshAxiomLocationPreferences.getPreferences().getFreshAxiomLocation() == FreshAxiomLocation.ACTIVE_ONTOLOGY);
        addFreshAxiomsToSubjectDefiningOntology = new JRadioButton("Add fresh axioms to subject defining ontology",
                FreshAxiomLocationPreferences.getPreferences().getFreshAxiomLocation() == FreshAxiomLocation.SUBJECT_DEFINING_ONTOLOGY);
        addFreshAxiomsToSubjectDefiningOntology.setToolTipText("Adds fresh axioms to the ontology where their subject is defined.  " +
                "If no such ontology exists then axioms are added to the active ontology.");
        axiomButtonGroup.add(addFreshAxiomsToActiveOntologyRadioButton);
        axiomButtonGroup.add(addFreshAxiomsToSubjectDefiningOntology);

        panel.addGroup("Axioms");
        panel.addGroupComponent(addFreshAxiomsToActiveOntologyRadioButton);
        panel.addGroupComponent(addFreshAxiomsToSubjectDefiningOntology);

        // Declarations

        keepDeclarationsInDefiningOntologyCheckBox = new JCheckBox(KEEP_DECLARATIONS_LABEL,
                DeclarationSynthesisPreferences.getPreferences().isKeepDeclarationsInDefiningOntology());
        keepDeclarationsInDefiningOntologyCheckBox.setToolTipText(KEEP_DECLARATIONS_TOOL_TIP);

        panel.addSeparator();
        panel.addGroup("Declarations");
        panel.addGroupComponent(keepDeclarationsInDefiningOntologyCheckBox);


        // Tree preferences

        OWLTreePreferences prefs = OWLTreePreferences.getInstance();
        autoExpandEnabledCheckBox = new JCheckBox("Expand trees by default", prefs.isAutoExpandEnabled());
        paintLinesCheckBox = new JCheckBox("Show parent-child lines in trees", prefs.isPaintLines());
        autoExpandMaxDepthSpinner = new JSpinner(new SpinnerNumberModel(prefs.getAutoExpansionDepthLimit(), 1, Integer.MAX_VALUE, 1));
        autoExpandMaxChildSizeSpinner = new JSpinner(new SpinnerNumberModel(prefs.getAutoExpansionChildLimit(), 1, Integer.MAX_VALUE, 1));

        dragAndDropEnabled.setSelected(prefs.isTreeDragAndDropEnabled());

        panel.addSeparator();
        panel.addGroup("");
        panel.addGroupComponent(autoExpandEnabledCheckBox);
        panel.addGroup("Number of levels shown");
        panel.addGroupComponent(autoExpandMaxDepthSpinner);
        panel.addGroup("Maximum child cut off");
        panel.addGroupComponent(autoExpandMaxChildSizeSpinner);
        panel.addVerticalPadding();
        panel.addGroupComponent(dragAndDropEnabled);
        panel.addGroupComponent(paintLinesCheckBox);

        // Search

        panel.addSeparator();
        panel.addGroup("Search type");
        panel.addGroupComponent(searchManagerPluginComboBox);
        SearchManagerSelector selector = getOWLEditorKit().getSearchManagerSelector();
        Collection<SearchManagerPlugin> plugins = selector.getPlugins();
        if (!plugins.isEmpty()) {
            for(SearchManagerPlugin plugin : plugins) {
                searchManagerPluginComboBox.addItem(plugin);
                if(plugin.getId().equals(getOWLEditorKit().getSearchManagerSelector().getCurrentPluginId())) {
                    searchManagerPluginComboBox.setSelectedItem(plugin);
                }
            }
            searchManagerPluginComboBox.setRenderer(new SearchManagePluginListCellRenderer());
            searchManagerPluginComboBox.setEnabled(true);
        }
        else {
            searchManagerPluginComboBox.setEnabled(false);
        }
    }

    public void initialise() throws Exception {
        createUI();
    }

    public void dispose() {
    }

    public static void main(String[] args) throws Exception {
        GeneralPreferencesPanel pp = new GeneralPreferencesPanel();
        pp.initialise();
        JDialog dlg = new JDialog();
        dlg.getContentPane().add(pp);
        dlg.pack();
        dlg.setVisible(true);
    }
}
