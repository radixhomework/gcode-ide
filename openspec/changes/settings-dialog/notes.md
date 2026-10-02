# Notes

- Amendments during apply (from the maintainer's review of the running app):
  - The window is a plain Stage, not a Dialog: `Dialog.close()` no-ops on
    this stack (Java 25 / JavaFX 25 / Windows) — the user could not close it.
    Stage show/close is the same proven path as the main window. Tests keep
    the `closeWindow()` seam (hide() can hang in Platform.startup test JVMs).
  - Theme commit applies the UA stylesheet globally
    (`Application.setUserAgentStylesheet`) so already-created scenes (the
    main window) restyle immediately; ThemeManager alone only covered
    later-created scenes.
  - The editor follows the selected theme: editor.css now uses AtlantaFX's
    looked-up palette variables (-color-fg-muted, -color-accent-fg,
    -color-danger-fg, -color-done-fg, -color-accent-subtle, ...) instead of
    literal light-theme colors, so syntax colors flip with dark themes.
  - Button bar moved to the lower-right corner (spacer-first), Cancel
    nearest to the right and bottom borders (equal 12px distances); the
    stray DialogPane "Close" button removed (ButtonType.CLOSE registration
    deleted); window sized 640x440, resizable, min 520x380.
