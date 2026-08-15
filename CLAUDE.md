# Working on Protégé with an AI agent

Claude Code reads this file automatically; Codex and other agents read `AGENTS.md`, which points here. It carries no rules of its own: it says where the project's knowledge lives and what to file back, so an agent working in this repo does not depend on whatever is configured on one developer's laptop.

## Where the knowledge is

- **Project knowledge base**: `musen-lab/protege-llm-wiki` (private, team members only). The contract frame, decisions and their reasoning, the contribution workflow, and practices discovered while doing the work. Clone it beside this repo. Read its `CLAUDE.md` before writing anything into it; that file is the schema and it is binding.
- **Codebase mechanics**: `musen-lab/protege-docs`, Josef Hardi's generated architecture documentation: module architecture, plugin API contracts, component inventories. Cite it by path. Check the source commit it was generated against before trusting counts or line numbers.
- **Guided course**: Inside Protégé, https://protege-code-tutorial.vercel.app (source: `musen-lab/protege-code-tutorial`). Ten lessons from the module map through startup, ontology loading, UI assembly, the change and undo pipeline, extension discovery, plugin authoring, and frame editing. Start here if the codebase is new to you. Its cutaways are pinned to a fixed commit, so verify against current code before acting on a line reference.

Answering a question about how this project works means searching the wiki's `wiki/`, then its `inbox/` and `raw/`, and trying the subject's other names, before concluding that something is undocumented. An unprocessed capture is knowledge the team already has.

## What to file back

End a substantive session by writing a capture note into the wiki's `inbox/`: a milestone with substance, a decision and its reasoning, a gotcha that cost real time, a dead end deliberately ruled out, or a correction to something the wiki currently states. Routine progress already visible in commits does not qualify.

The wiki ships the rules and the note format as a skill at `.agents/skills/protege-wiki-capture/`. Symlink it into your agent's skills directory, or simply read it. Coding sessions write to `inbox/` only; editing `wiki/` pages is ingest work done inside the wiki under its own rules.

## Branching and PRs

The team's workflow is recorded on the wiki's Contribution Strategy page: project-task branches `feat/<T#>-<Label>` off main, dev-task branches beneath them, PR review into the project-task branch as the first gate, then an upstream resync, a squash-and-merge to main, and the upstream PR as the second gate. That page is authoritative; this paragraph is a signpost, not a substitute.
