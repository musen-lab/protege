## 1. Force both checks on in the model

- [x] 1.1 Add `DeclarationChecker.forcingAllChecks()` that forces the suppression gate on and binds
      the misplaced checker to every registered ownership rule, reading no preference and modifying
      nothing.
- [x] 1.2 Add a test proving `forcingAllChecks()` runs both checks (EXECUTED) and finds the missing
      and misplaced entities on a closure the save-time path would skip.

## 2. Report dialog

- [x] 2.1 Add `DeclarationCheckReportPanel` modeled on `PhysicalLocationPanel`: a resizable
      `JOptionPane(PLAIN_MESSAGE)` host with an `MList` in a scroll pane.
- [x] 2.2 Build rows from `DeclarationReportOutline`: a counted section header per section, entity
      rows rendered with `OWLCellRenderer`, and the ownership-rule label on misplaced rows via
      `DeclarationReportOutline.ruleLabelOf`.
- [x] 2.3 Show an explicit placeholder row when a section is empty so an all-empty report is an
      unmistakable no-issues result.
- [x] 2.4 Add tests for row grouping, misplaced rule labels, and the empty-section placeholders.

## 3. Tools menu action

- [x] 3.1 Add `CheckDeclarationsAction` that runs `forcingAllChecks()` on the active ontology and
      shows the report, presenting a check failure as an error dialog.
- [x] 3.2 Register the action under the Tools menu in `plugin.xml`.

## 4. Verify

- [x] 4.1 Confirm the save-time warning (`DeclarationIssuesReporter`/`DeclarationIssuesDialog`) and
      the declaration preferences are unchanged.
- [x] 4.2 Run the `protege-editor-owl` test suite green.
