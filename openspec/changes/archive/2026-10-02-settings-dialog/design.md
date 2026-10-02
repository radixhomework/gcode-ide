# Design: settings-dialog

## Context

Builds on `theme-settings` (implemented): ThemeManager switching, `theme`
config key, `applyTheme`/persister wiring all exist. This change relocates
the surface into a dedicated dialog and introduces **staged** semantics
(Apply/OK/Cancel) replacing the initially-planned live-apply — per the
maintainer's refinement.

## Decisions

### D1: JavaFX `Dialog` with a `TabPane`, categories as tabs
`view/SettingsDialog` builds a `Dialog<ButtonType>` whose pane is a
`TabPane`; each tab is one settings category, starting with **Appearance**
(theme choice as a `ComboBox<String>` of installed theme names). Custom
button bar: **Apply**, **OK**, **Cancel** (`Dialog` buttons
`applyType/okType/cancelType`; Apply keeps the dialog open via the standard
result-converter + `event.consume()` pattern). Shown via `show()`.

### D2: Staged state per category, committed on Apply/OK
The dialog keeps a pending value per control (staged on combo selection),
applied to nothing until commit. **Apply**: commit all categories (apply +
persist) and keep the window open. **OK**: commit + close. **Cancel**:
close, discarding pending values — already-committed changes stay (standard
convention; committed state is never rolled back). The theme commit reuses
the existing `ThemeManager.setTheme` + theme persister path
(`controller.selectTheme` semantics move into the dialog via injected
commit hooks).

### D3: Commit hooks injected, controller owns the wiring
`SettingsDialog` receives the theme list (names + instances), a commit
callback `Consumer<String> themeCommit` (App/controller wires it to
ThemeManager + config persist), and the committed value for initialization.
Future categories add another control + commit hook per tab — no registry
abstraction yet (the TabPane + hooks convention is the extension point).

### D4: Restart-required marking
A control factory wraps any restart-required setting with a muted
"takes effect after the application is closed and reopened" label under the
control, and its commit hook persists only (no live action). No current
setting uses it; a fixture in the tests exercises the label and the
persist-only commit.

### D5: Wiring
`File > Settings...` (FXML, Ctrl+,) calls `controller.onOpenSettings()`,
which constructs and shows `SettingsDialog`. The `settingsMenu` element,
`buildThemeMenu`, and `themeMenuForTest` are removed. Theme
restore-before-scene in `App` is unchanged (restore stays non-persisting).

## Risks / Trade-offs

- [Committed-but-then-Cancel surprises users expecting full rollback] →
  standard convention (Apply is sticky); OK/Apply make the commitment
  explicit.
- [Primer styling of Dialog button bar] → Primer styles Dialog buttons
  adequately; token classes reused if needed.

## Migration Plan

UI relocation + staged semantics; config format unchanged. Rollback = revert
commit.

## Open Questions

- none blocking
