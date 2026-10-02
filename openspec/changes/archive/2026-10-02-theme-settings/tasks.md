# Tasks: theme-settings

- [x] 1.1 FXML: remove the Machine label + profile combo; add the Settings menu with a Theme submenu placeholder
- [x] 1.2 Controller: populate the Theme submenu with the installed themes as radio items; selection applies via `ThemeManager` immediately and invokes the injected persister; remove the profile-combo wiring and accessors
- [x] 1.3 App: restore the persisted theme before scene creation and pass the persister (`theme` key in config.yaml); default Primer Light
- [x] 1.4 Tests: replace the profile-switch test with one asserting the internal default profile still drives warnings; add a theme-persistence test (selection → config key → restored instance); full suite green, README note, rebuild, relaunch
