# AGENTS.md — working rules for coding agents

Rules for AI agents (and anyone acting as one) working in this repository.
Project-specific details live in the repo's own documentation (README.md
and the specs under `openspec/`) — read them before touching code.
(Adapted from the organization `default-template`; where the template and
this repository disagreed, this repository's practice prevailed and the
sections that did not apply were removed.)

## Commit policy

- **Never commit or push unless the user explicitly asked for it.**
  Finishing a task or passing tests is never consent to commit.
- If the user asks to hold for local testing, report "done, ready to test"
  and stop — don't ask again; wait for an explicit go.
- Descriptive imperative commit subjects, English, body bullets explaining
  the why ("Remove machine-profiles capability; fix 3D camera orientation").
  Work lives on the long-lived `feat/java-design` branch, pushed directly
  (no PR ceremony unless asked).
- When the working tree contains files the agent did not create, inspect
  them and say so before staging everything.

## Agent-local files are never committed

- **Never stage or commit agent-specific directories and files**
  (`.zcode/`, `.claude/`, `.agents/`, `.cursor/`, `.aider*`, and the like).
  They are machine-local configuration, not project content. `.zcode/` is
  covered by this repo's `.gitignore`; keep it that way.

## Quality gate

This repository has no SonarCloud/CodeQL setup — the quality gate is the
local build:

- `mvn test` passes (plain-JUnit model/service suites + TestFX view suites,
  currently 110+ tests), and `mvn -q package` builds the shaded jar.
- For 3D/camera/rendering changes, verify with a rendered-PNG probe (see
  the probes in the git history) — transform math that passes unit tests
  has still rendered inverted on screen.

## Iteration policy

Fix what fails, re-run `mvn test`, repeat until green.

**Stop rule: 3 iterations maximum, autonomously.** After 3 fix/verify
iterations, stop and report what was tried; wait for the user's decision.

## Testing stance

Unlike the default minimal stance, this repository maintains **substantial
regression suites** (plain-JUnit model/service tests + TestFX view
integration tests, 104+ as of the profile removal) and they are part of
"done": `mvn test` must pass before any task is called complete. Keep new
features covered — pure-model logic headlessly, views via TestFX with
stubbed choosers/prompts so no real modal can block a run.

## Workflow

- Feature work goes through OpenSpec (`openspec/`): `/opsx:propose`
  (planning only — never implement in the same turn), `/opsx:apply`
  (task-by-task, tick checkboxes), `/opsx:archive` when done (the sync
  step updates the main specs under `openspec/specs/`). Spec scenarios are
  the acceptance criteria.
- **Documentation is part of the workflow**: README sections, spec updates,
  and task-list ticks are maintained as the change progresses — not
  deferred to "later". `/opsx:archive` is only done once the documentation
  reflects the implemented behavior.
- This repo is machine-profile-free **by decision** (change
  `remove-machine-profiles`): no bed envelopes, feed caps, or profile
  selection. Do not reintroduce them without an approved change.

## Product & architecture documentation

This repository uses **README.md** (product overview, quickstart,
packaging) plus the OpenSpec specs under `openspec/specs/` (behavioral
contracts) instead of separate PRODUCT.md / ARCHITECTURE.md files. Keep
them up to date in the same change that alters behavior or structure —
the `openspec archive` sync step handles the spec side; README edits go in
the same commit series. When starting a task, read the README and the
relevant spec first; if the code and the docs disagree, surface the
discrepancy to the user instead of silently trusting either one.

## Documentation edits need approval

`AGENTS.md` is never edited silently: substantive changes (new decisions,
scope changes, removed sections) are proposed to the user and applied
after approval. Routine sync of facts that an approved change already
implies (e.g. documenting the feature being merged) goes in directly.

## Repo-specific conventions

- **Stack**: Java 25 LTS, JavaFX 25, Maven; RichTextFX editor, AtlantaFX
  theme, SnakeYAML, Lombok, SLF4J/Logback; TestFX for GUI tests. Windows
  is the dev machine; the app image is built with jpackage (JavaFX jmods
  fetched from Gluon, `java.logging` module included — SnakeYAML needs it).
- **Architecture boundary**: everything under `model/` is pure Java with
  no JavaFX imports and must stay headless-testable; JavaFX lives only in
  `view/` and `App`. Services sit between and own state.
- **English everywhere**: UI strings, commits, docs, comments.
- **Build/test**: `mvn test`, `mvn javafx:run`, packaging via
  `jpackage` (see README). Dependency additions need a version check
  against Maven Central first.
