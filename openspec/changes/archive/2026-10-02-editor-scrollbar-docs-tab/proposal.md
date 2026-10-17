# Proposal: editor-scrollbar-docs-tab

## Why

Daily-use ergonomics gaps in the editor: long documents and long lines cannot
be navigated visually because the code area has no scroll bars; the
documentation popup disappears the instant the mouse moves (you cannot read
it comfortably, let alone move toward it); and accepting a completion
requires Enter or a click, where Tab is the convention every IDE user
expects.

## What Changes

- **Editor scroll bars**: the code area gains a vertical scroll bar (right)
  and a horizontal scroll bar (bottom), visible whenever the document or the
  longest line exceeds the visible pane. Scrolling, goto-line, and the
  current-line highlight keep working through the wrapper.
- **Sticky documentation popup**: the 2-second hover still opens the popup,
  but once open, moving the mouse no longer dismisses it. It stays while the
  cursor remains on the triggering keyword and hides when the cursor leaves
  that keyword, on any key press, on a mouse click, or when the document
  text changes.
- **Tab-validated completion**: when the completion popup is open, pressing
  Tab accepts the selected suggestion (in addition to Enter and click) and
  must not insert a tab character into the document.
- **Go-to-line**: an Edit > "Go to Line..." action (shortcut Ctrl+G) opens a
  small input dialog and navigates the editor to the requested line (caret
  placed, line selected and scrolled into view). Empty, non-numeric, or
  out-of-range input is ignored without moving the caret.
- Housekeeping: the key-press event filter is currently registered twice
  (a latent double-handling of Enter/Escape); the registration is
  de-duplicated.
- Non-goals: custom scroll-bar styling beyond the theme; minimap; changing
  completion trigger behavior (still automatic on word start).

## Capabilities

### New Capabilities

(none — refinements to the existing `gcode-editor` capability)

### Modified Capabilities

- `gcode-editor`: ADDED "Editor scroll bars"; ADDED "Tab-validated
  completion"; ADDED "Go to line"; MODIFIED "Hover and shortcut
  documentation" (dismissal semantics: movement no longer hides an open
  popup — full updated text in the delta).

## Impact

- **Code**: `GCodeEditorView` (wrap the CodeArea in Flowless's
  `VirtualizedScrollPane`, sticky-doc anchor tracking, Tab key handling,
  de-duplicated filter), tests (scroll bar presence + goto-line through the
  wrapper, popup stickiness, Tab acceptance), README.
- **Dependencies**: none new — Flowless (`VirtualizedScrollPane`) is already
  on the classpath as a RichTextFX transitive.
- **Ecosystem**: none.
