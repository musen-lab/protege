## Purpose

Lets a user audit the active ontology and its import closure for missing and misplaced entity
declarations at any time, through a read-only menu command whose result is shown in a grouped
report dialog, independent of the save-time declaration warning and its preferences.

## ADDED Requirements

### Requirement: On-demand declaration check command

The application SHALL provide a command under the Tools menu that checks the active ontology and
its import closure for missing and misplaced entity declarations.

#### Scenario: Command is available under Tools

- **WHEN** an OWL ontology is open and the user opens the Tools menu
- **THEN** a menu item to check entity declarations is shown

#### Scenario: Running the command produces a report

- **WHEN** the user invokes the check command
- **THEN** the system checks the active ontology and its import closure and shows a report of the
  findings

### Requirement: Both checks run regardless of Axioms preferences

The command SHALL run both the missing- and misplaced-entity-declaration checks even when the
Axioms preferences - the suppress-automatic-entity-declarations setting or the enabled ownership
rules - would cause the save-time path to skip either check.

#### Scenario: Checks run while automatic declarations are not suppressed

- **WHEN** the suppress-automatic-entity-declarations preference is off and the user invokes the
  check command
- **THEN** both the missing- and misplaced-declaration checks still run and their findings are
  reported

#### Scenario: Misplaced check runs while no ownership rule is enabled in preferences

- **WHEN** no ownership rule is enabled in the Axioms preferences and the user invokes the check
  command
- **THEN** the misplaced-declaration check still runs using the registered ownership rules

### Requirement: Read-only operation

The command SHALL NOT change any preference and SHALL NOT modify any ontology axioms. Running it
SHALL NOT alter the behavior of the save-time declaration warning.

#### Scenario: No preference or ontology change

- **WHEN** the user invokes the check command
- **THEN** no Axioms preference value changes and no axiom is added to or removed from any ontology

### Requirement: Grouped report presentation

The report SHALL present findings in a resizable dialog containing a scrollable list, grouping them
into a missing-entity-declarations section and a misplaced-entity-declarations section. Each section
SHALL show its finding count, and each finding SHALL appear as a readable per-entity row. A
misplaced finding's row SHALL identify the ownership rule that selected it.

#### Scenario: Findings grouped into two counted sections

- **WHEN** the check finds missing and misplaced declarations
- **THEN** the report shows a missing-entity-declarations section and a misplaced-entity-declarations
  section, each headed by its count, with one row per finding

#### Scenario: Misplaced row names its ownership rule

- **WHEN** the report shows a misplaced-declaration finding
- **THEN** that finding's row shows the label of the ownership rule that identified it

### Requirement: Explicit no-issues and failure results

The report SHALL show an explicit no-issues result when both sections are empty. If the check
fails, the system SHALL present the failure as an error rather than a clean report.

#### Scenario: No issues found

- **WHEN** the check finds no missing and no misplaced declarations
- **THEN** the report explicitly states that no missing or misplaced entity declarations were found

#### Scenario: Check failure

- **WHEN** the check fails with an error
- **THEN** the system shows an error rather than an empty or clean report
