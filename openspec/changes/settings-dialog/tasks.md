# Tasks: settings-dialog

- [x] 1.1 Implement `view/SettingsDialog` (tabbed dialog per design D1: Appearance tab with the staged theme combo; Apply/OK/Cancel button bar with commit-keep-open / commit-close / discard semantics; restart-required wrapper per D4)
- [x] 1.2 FXML/controller: add File > Settings... (Ctrl+Comma) opening the dialog with the commit hooks wired (ThemeManager + `theme` config key); remove the Settings menu, `buildThemeMenu`, and `themeMenuForTest`
- [x] 1.3 Tests: dialog-driven suite covering Apply (commit + stays open + persisted), OK (commit + closes), Cancel (discard — previous theme still active and config unchanged), the restart-required fixture (mention rendered, commit persists only), and the restore/unknown-name paths; full `mvn test` green
- [x] 1.4 README note (Settings dialog semantics); rebuild the app image and verify by hand: File > Settings opens the window, staged theme commit via Apply and OK, Cancel discards
