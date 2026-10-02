## Purpose

Defines the settings surface: the File > Settings entry that opens a dedicated, categorized settings window with staged application semantics (Apply / OK / Cancel), restart-required mentions, and theme selection as its first section.

## Requirements

### Requirement: Settings entry opens a dedicated settings window
The File menu SHALL contain a Settings... entry (accelerator Ctrl+Comma) that opens a dedicated settings window organized as categorized sections (tabs), starting with an Appearance section. The menu bar SHALL NOT contain a separate Settings submenu with inline settings.

#### Scenario: Opening settings
- **WHEN** the user activates File > Settings...
- **THEN** a dedicated settings window opens showing the categorized sections, with Appearance first

#### Scenario: No inline settings submenu
- **WHEN** the menu bar is inspected
- **THEN** there is no top-level Settings menu; settings are reached only through File > Settings...

### Requirement: Staged application with Apply, OK, and Cancel
The settings window SHALL present three buttons: Apply, OK, and Cancel. Edits SHALL be staged while the window is open - nothing takes effect until Apply or OK. Apply SHALL commit the staged changes (applying and persisting them) and keep the window open; OK SHALL commit the staged changes and close the window; Cancel SHALL close the window and discard all uncommitted changes.

#### Scenario: Apply commits and keeps the window open
- **WHEN** the user changes a setting and presses Apply
- **THEN** the change takes effect and is persisted, and the settings window remains open

#### Scenario: OK commits and closes
- **WHEN** the user changes a setting and presses OK
- **THEN** the change takes effect, is persisted, and the settings window closes

#### Scenario: Cancel discards uncommitted edits
- **WHEN** the user changes a setting without committing and presses Cancel
- **THEN** the settings window closes and the setting keeps its previously committed value

### Requirement: Restart-required settings are marked
A setting that cannot be applied to the running application SHALL be visibly marked in the window with a mention stating that it takes effect after the application is closed and reopened. Committing such a setting SHALL only persist it; it SHALL NOT attempt to apply it live.

#### Scenario: Restart-required setting is labeled
- **WHEN** the settings window contains a setting marked as restart-required
- **THEN** its row displays the mention that it applies after the application is closed and reopened

#### Scenario: Committing a restart-required setting
- **WHEN** the user commits (Apply or OK) a change to a restart-required setting
- **THEN** the new value is persisted but the running application's behavior is unchanged until the next start

### Requirement: Theme selection in the Appearance section
The Appearance section SHALL offer the installed themes as exclusive choices. The selected theme SHALL be staged while editing and SHALL take effect (applied to the whole UI and persisted) when the user presses Apply or OK. The default remains Primer Light when nothing was ever selected, and the persisted choice SHALL be restored on application startup.

#### Scenario: Switching to dark mode via Apply
- **WHEN** the user selects Primer Dark in the Appearance section and presses Apply
- **THEN** the UI restyles immediately, the choice is persisted, and the window stays open

#### Scenario: Cancel keeps the previous theme
- **WHEN** the user selects a different theme and then presses Cancel without committing
- **THEN** the previously committed theme remains active
