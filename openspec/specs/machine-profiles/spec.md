## Purpose

Defines machine definitions as plain YAML data - bed envelope, feed limits, safe Z - that drive the preview and safety diagnostics, ship with a generic example preset, and are readable by external tools such as grbl-machine-controller without shared code. The application targets no specific machine vendor or model.

## Requirements

### Requirement: Generic example profile
The application SHALL ship a generic, machine-neutral example profile (documented bed envelope, feed limits, safe Z in millimeters) instead of any vendor- or model-specific profile. No machine vendor or model SHALL be named in the application UI, built-in data, or documentation as the tool's target; machine examples may mention dimensions only.

#### Scenario: Example profile ships and loads
- **WHEN** the application starts
- **THEN** a generic example profile is available and active by default, declaring positive bed extents, feed limits, and safe Z

#### Scenario: No vendor branding anywhere
- **WHEN** the UI, built-in profile data, README, and project documentation are inspected
- **THEN** no machine vendor or model name appears as the tool's designated target

### Requirement: YAML profile format
Machine profiles SHALL be plain YAML files with documented, stable field names (machine identity, bed extents, feed limits, safe Z), intended as an interchange format readable by grbl-machine-controller without any shared code.

#### Scenario: External readability
- **WHEN** a profile file is read by a generic YAML parser
- **THEN** the bed extents, feed limits, and safe Z are present under their documented field names, in millimeters

### Requirement: Active profile selection
The user SHALL be able to select the active machine profile from the available profiles; the selection SHALL persist across application restarts, and the preview and all diagnostics SHALL use the active profile.

#### Scenario: Switching profiles
- **WHEN** the user selects a different machine profile
- **THEN** the bed rendering, out-of-bed checks, and feed-cap checks are recomputed against the newly selected profile

#### Scenario: Selection persists
- **WHEN** the application is restarted after selecting a profile
- **THEN** that profile is active again without user action

### Requirement: User profile directory
The application SHALL recognize user-supplied profile files placed in a per-user configuration directory and list them alongside built-in profiles. A malformed profile file SHALL be skipped with a diagnostic, without preventing other profiles from loading.

#### Scenario: Adding a custom machine
- **WHEN** a valid profile YAML file is placed in the user configuration directory and profiles are refreshed
- **THEN** that machine appears in the profile selector

#### Scenario: Malformed profile is quarantined
- **WHEN** the user configuration directory contains a profile with invalid or missing required fields
- **THEN** that file is skipped with a diagnostic explaining why, and all other profiles remain available

### Requirement: Profile validation
A profile SHALL be accepted only if it declares all required fields with sane values (positive bed extents and feed limits); anything else SHALL be rejected with a reason naming the offending field.

#### Scenario: Rejection with a named field
- **WHEN** a profile declares a zero or negative bed extent
- **THEN** the profile is rejected and the reason names the offending field
