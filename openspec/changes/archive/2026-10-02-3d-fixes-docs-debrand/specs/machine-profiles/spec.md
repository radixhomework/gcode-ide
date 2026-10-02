## Purpose

Defines machine definitions as plain YAML data — bed envelope, feed limits, safe Z — that drive the preview and safety diagnostics, and are readable by external tools such as grbl-machine-controller without shared code.

## ADDED Requirements

### Requirement: Generic example profile
The application SHALL ship a generic, machine-neutral example profile (documented bed envelope, feed limits, safe Z in millimeters) instead of any vendor- or model-specific profile. No machine vendor or model SHALL be named in the application UI, built-in data, or documentation as the tool's target; machine examples may mention dimensions only. (Supersedes the earlier "Built-in PROVER 3018 profile" requirement wording on this branch.)

#### Scenario: Example profile ships and loads
- **WHEN** the application starts
- **THEN** a generic example profile is available and active by default, declaring positive bed extents, feed limits, and safe Z

#### Scenario: No vendor branding anywhere
- **WHEN** the UI, built-in profile data, README, and project documentation are inspected
- **THEN** no machine vendor or model name appears as the tool's designated target
