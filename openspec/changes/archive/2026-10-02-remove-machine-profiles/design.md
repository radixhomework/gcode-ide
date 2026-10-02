# Design: remove-machine-profiles

## Context

The machine-profiles capability threads through the app: model (`model/
profiles/`, `Diagnostics` profile parameters), services (`ProfileService` —
which also owns config persistence), views (bed box, profile combo), tests,
docs, and the pending `svg-import-wizard` artifacts. Removal must keep the
config persistence (window geometry, `last_open_dir`) that happens to live in
`ProfileService` today.

## Decisions

### D1: Config persistence survives in a new `ConfigService`
Extract the config halves of `ProfileService` (`loadConfig`,
`updateConfig`/`writeConfig`) into `service/ConfigService` (SnakeYAML stays);
`App` uses it for window geometry and the choosers for `last_open_dir`.
`active_profile` is no longer read or written; an existing key in a user's
`config.yaml` is left untouched (harmless). `model/profiles/` and the profile
loader are deleted with their tests.

### D2: Statistics and warnings go profile-free
`ToolpathStats` keeps bbox/cut/rapid distance; the time estimate becomes
`Σ(cut_length / feed)` over cut moves **with** a commanded feed — rapids and
feedless cuts contribute distance but no time. Warnings shrink to the pure
dialect check: missing-feed on cut moves (message no longer mentions any
cap). `Diagnostics` loses the `MachineProfile` parameter everywhere;
`moveOutOfBed` and feed-cap checks are deleted with their tests.

### D3: Preview loses the bed, keeps its framing
`Preview3DView` drops the bed `Box` and the profile parameter; the fit/
orientation logic re-targets from bed extents to the toolpath bounding box
(camera distance from content bounds, world centering recomputed per
toolpath). The existing visibility regression (`bedVisibleFromCamera`)
becomes content-bounds-based; orientation/chirality tests re-anchor on the
same bed-coordinate→world mapping minus the bed box (mapping constants move
from profile extents to content bounds). Stale overlay, picking, navigation
untouched.

### D4: UI wiring
Status bar loses the "Machine:" label and combo (keeps caret, statistics,
warnings); FXML/controller simplified; document/parse flows unchanged.

### D5: Pending artifacts updated in-step
`svg-import-wizard` (proposed, unimplemented) referenced bed clamping and
placement on the bed: its artifacts are amended by this change's tasks —
placement stays a free X/Y offset with no clamp, the bed-containment round
trip becomes a bounds-containment check, and wizard params drop nothing else.
`openspec/config.yaml` context and README drop profile mentions
(the machine-profile interchange sentence is removed, not rewritten).

## Risks / Trade-offs

- [Users lose envelope safety checks] → Accepted by the removal decision;
  the tool is positioning-system-agnostic now.
- [Estimated time loses rapid leg] → The figure stays labeled an estimate;
  cut-only time is the honest number without a rapid rate.
- [Content-bounds framing behaves differently for tiny toolpaths] → Minimum
  camera distance keeps the view sane; covered in the reframed regression
  tests.

## Migration Plan

No data migrations (stale config keys ignored). Rollback is reverting the
deletion commits. Order: extract `ConfigService` first (nothing breaks), then
strip profile consumers, then delete the capability, then update pending
artifacts and docs.
