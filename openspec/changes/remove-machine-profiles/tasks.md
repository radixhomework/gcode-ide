# Tasks: remove-machine-profiles

## 1. Extract config persistence

- [x] 1.1 Implement `service/ConfigService` (load/update/write, SnakeYAML) from `ProfileService`'s config halves; `App` (geometry, `last_open_dir`) and tests migrate to it; a stale `active_profile` key in an existing config is ignored
- [x] 1.2 Verify window-geometry round-trip and last-directory tests pass against `ConfigService`

## 2. Model: statistics and warnings

- [x] 2.1 Change `Diagnostics`/`ToolpathStats` to the profile-free API (cut-only estimated time; missing-feed warning without cap; delete `moveOutOfBed` and feed-cap checks); update the diagnostics tests to the new hand-computed expectations (feedless and rapid moves add no time)
- [x] 2.2 Delete `model/profiles/` (MachineProfile, ProfileException) and their tests

## 3. Views and wiring

- [x] 3.1 Remove the bed box and profile parameter from `Preview3DView` (fit/orientation re-targeted to toolpath bounds; visibility and chirality regression tests re-anchored); remove the Machine label + profile combo from FXML/controller and the profile wiring from `App`
- [x] 3.2 Delete `ProfileService` and its tests; grep the repo for remaining profile references in code and tests
- [x] 3.3 Update controller/status-bar tests (statistics rendering without profile, warnings now only missing-feed, profile-switch tests removed)

## 4. Docs, context, pending artifacts

- [x] 4.1 Update README (no profiles; statistics semantics) and `openspec/config.yaml` context (drop the profile/interchange sentences); amend the pending `svg-import-wizard` artifacts: free placement without bed clamp, bounds-based round trip
- [x] 4.2 Full `mvn test` green; rebuild the app image; verify by hand: app starts profile-free (no Machine selector, no bed box), statistics show cut-only time, warnings only for feedless cuts
