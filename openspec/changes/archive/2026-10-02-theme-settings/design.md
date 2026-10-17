# Design: theme-settings

## Decisions

### D1: Theme list mirrors the installed AtlantaFX set
Controller-side list of (label, theme instance): Primer Light/Dark,
Nord Light/Dark, Cupertino Light/Dark, Dracula. `RadioMenuItem`s in
Settings > Theme; selection calls `ThemeManager.setTheme(...)` (applies live
to scenes the manager tracks) and invokes an injected persister.

### D2: Persistence reuses the existing config map
`App` passes a persister writing `theme` = theme name via
`ProfileService.updateConfig`, and restores it before the main Scene is
created (default: Primer Light). No new service; the pending
`remove-machine-profiles` later moves this to `ConfigService`.

### D3: Dropdown removal is UI-only
FXML loses the Machine label + combo; the controller drops the combo wiring
and `selectProfile`/`activeProfileName` accessors; `ProfileService` stays as
the internal default-profile provider (diagnostics, 3D bed). The integration
test for profile switching is replaced by one asserting the internal default
still drives warnings.

## Risks / Trade-offs

- [ThemeManager live-apply behavior across popups] → Popups carry their own
  stylesheet (app.css) and tokens; AtlantaFX themes the UA layer for tracked
  scenes — verified visually after rebuild.
