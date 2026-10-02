## Purpose

Defines machine definitions as plain YAML data — bed envelope, feed limits, safe Z — that drive the preview and safety diagnostics. **This capability is removed from the product in every phase.**

## REMOVED Requirements

### Requirement: Generic example profile
- **Reason**: the tool is a general GRBL G-code workbench, not a machine-bound one; curating per-machine data (even an example) is unwanted maintenance and couples the UI to a vendor-style concept.
- **Migration**: none — the built-in example profile file and its loading are deleted; the app starts profile-free with no selector.

### Requirement: YAML profile format
- **Reason**: the plain-YAML machine profile interchange format (originally designed to be readable by grbl-machine-controller without shared code) is abandoned together with the capability; the only cross-tool handoff remains a `.nc` file on disk.
- **Migration**: existing profile YAML files in user directories are simply no longer read; users keep them as plain files if they wish. No data conversion.

### Requirement: Active profile selection
- **Reason**: with no profiles there is nothing to select; the status-bar selector, its persistence (`active_profile` in `config.yaml`), and profile-driven recomputation of rendering and diagnostics all go away.
- **Migration**: a stale `active_profile` key in an existing `config.yaml` is ignored (and left in place harmlessly); window-geometry and last-directory persistence are unaffected.

### Requirement: User profile directory
- **Reason**: no profiles to load; the per-user `gcode-ide/profiles` directory feature is deleted.
- **Migration**: files left in that directory are ignored by the app.

### Requirement: Profile validation
- **Reason**: validation existed to gate profile loading; with profiles gone there is nothing to validate.
- **Migration**: `MachineProfile` and its validation tests are deleted outright.
