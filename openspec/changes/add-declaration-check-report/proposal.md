## Why

The missing- and misplaced-entity-declaration checks only run at save time, and only when the
Axioms preferences (the "Suppress automatic entity declarations" setting and the enabled ownership
rules) happen to turn them on. A user who wants to audit the current ontology for declaration
problems cannot do so on demand without first changing those preferences. There is no way to ask
"what declaration issues does this ontology have right now?" and get an answer.

## What Changes

- Add a **Tools** menu item that runs both the missing- and misplaced-entity-declaration checks on
  the active ontology and its import closure on demand, regardless of the Axioms preference
  selections that would otherwise skip either check.
- Present the findings in a report dialog modeled on the existing "Loaded ontology sources" dialog:
  a resizable plain dialog containing a scrollable list, with the findings grouped into the two
  existing sections - missing entity declarations and misplaced entity declarations - each showing
  a count and readable per-entity rows. Every row shows the finding's severity; missing rows name
  the ontologies that use the entity, and misplaced rows name the ownership rule that identified
  them, the owning ontology, and the declaring ontologies.
- Show an explicit no-issues result when both sections are empty, and present a check failure as an
  error rather than a clean report.
- The action is read-only: it changes no preference and modifies no ontology axioms. The existing
  save-time warning behavior is unchanged.

## Capabilities

### New Capabilities
- `entity-declaration-report`: on-demand, read-only reporting of missing and misplaced entity
  declarations in the active ontology and its import closure, shown in a grouped dialog.

### Modified Capabilities
<!-- None: no existing spec-level behavior changes; the save-time warning is untouched. -->

## Impact

- `protege-editor-owl`: new `CheckDeclarationsAction` and `DeclarationCheckReportPanel` under
  `org.protege.editor.owl.ui.declaration`; a new `DeclarationChecker.forcingAllChecks()` factory in
  `org.protege.editor.owl.model.declaration`; a new Tools-menu `EditorKitMenuAction` entry in
  `plugin.xml`.
- Reuses the existing `DeclarationReportOutline`/report model as the presentation data source.
- No change to the save-time reporter (`DeclarationIssuesReporter`/`DeclarationIssuesDialog`), the
  preferences, or the declaration-check algorithms.
