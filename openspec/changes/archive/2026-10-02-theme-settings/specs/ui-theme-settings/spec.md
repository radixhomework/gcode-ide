## Purpose

Defines the Settings menu: runtime theme selection across the installed AtlantaFX themes, with immediate application and persistence across restarts.

## ADDED Requirements

### Requirement: Theme selection in a Settings menu
The application SHALL provide a Settings menu containing a Theme submenu listing the installed themes as exclusive choices. Selecting a theme SHALL apply it immediately to the whole UI, and the choice SHALL persist across application restarts.

#### Scenario: Switching to dark mode
- **WHEN** the user selects Primer Dark in Settings > Theme
- **THEN** the entire UI restyles with the dark theme immediately, without restarting

#### Scenario: Choice persists
- **WHEN** the application is restarted after selecting a theme
- **THEN** that theme is active without user action

#### Scenario: Default theme
- **WHEN** no theme has ever been selected
- **THEN** the application starts with Primer Light
