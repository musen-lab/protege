## Context

The declaration-detection feature already has a checker (`DeclarationChecker`), a report model
(`DeclarationReport` and the presentation helper `DeclarationReportOutline`), and a save-time
warning (`DeclarationIssuesReporter`/`DeclarationIssuesDialog`). See proposal.md - Why for the gap
this change fills. The save-time checker gates both checks on Axioms preferences: it returns an
all-skipped report unless automatic entity declarations are suppressed, and the misplaced check
additionally skips when no ownership rule is enabled.

## Goals / Non-Goals

- Goal: a manually invoked, read-only report that always runs both checks and reuses the existing
  report model for presentation.
- Non-Goal: export, autofix, or any other declaration workflow; changing the save-time warning;
  changing preferences or the check algorithms.

## Decisions

- **Force both checks on via a model-layer factory.** Add
  `DeclarationChecker.forcingAllChecks()`, which constructs the checker with the suppression gate
  forced on (`() -> true`) and the misplaced checker bound to every registered ownership rule
  (`OwnershipRules::registered`). Keeping the forcing in the model package lets the UI stay a thin
  caller and keeps the preference-reading constructors untouched for the save-time path. The
  factory reads no preference and modifies nothing, preserving the read-only guarantee.
- **Model the dialog on `PhysicalLocationPanel`.** `DeclarationCheckReportPanel` is a resizable
  `JOptionPane(PLAIN_MESSAGE)` host sized 800x500, containing an `MList` in a scroll pane - the
  same shape as the "Loaded ontology sources" dialog the captain referenced.
- **Reuse `DeclarationReportOutline` as the data source.** The panel builds its rows from the
  outline's sections and findings rather than recomputing anything. Section headers use each
  section's heading (title plus count); entity rows render with the shared `OWLCellRenderer`; a
  misplaced row's detail line uses `DeclarationReportOutline.ruleLabelOf`.
- **Explicit empty and failure results.** When a section has no findings the panel shows a
  placeholder row, so an all-empty report is an unmistakable no-issues result. The action catches a
  check failure and shows an error dialog instead of an empty report.

## Risks / Trade-offs

- [Embedding `OWLCellRenderer` inside the panel's cell renderer] → the renderer is used as a
  rubber-stamp component and re-parented per paint, the same pattern Swing cell renderers rely on;
  the real `MList` and row index are passed through so width and link handling behave normally.
- [Forcing checks on could surprise a user who turned a check off] → acceptable and intended: the
  command's purpose is to report regardless of the preference selections, and it never writes
  anything back.

## Migration Plan

Not applicable - additive feature with no data or preference migration.
