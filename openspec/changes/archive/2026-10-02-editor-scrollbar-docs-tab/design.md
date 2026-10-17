# Design: editor-scrollbar-docs-tab

## Context

All three refinements live in `GCodeEditorView`. Grounded observations from
the code: the `CodeArea` is placed directly in the SplitPane (no scroll
wrapper); `hideDocumentation()` fires on every `MOUSE_MOVED` filter event and
the hover timer restarts on each move; `onKeyPressed` handles
Escape/Enter/Up/Down but not Tab; and the key-press filter is registered
twice (once for popup keys, once for Ctrl+Q) — Enter/Escape currently run
twice per press.

## Decisions

### D1: Scroll bars via Flowless `VirtualizedScrollPane`
Wrap the `CodeArea` in `org.fxmisc.flowless.VirtualizedScrollPane` (flowless
0.7.4 is already a RichTextFX transitive — verified in the local repository;
the jar provides both vertical and horizontal bars for `Virtualized` nodes,
which `CodeArea` implements). `node()` returns the wrapper; the SplitPane,
min-size handling, and theme attachments move to the wrapper. `gotoLine`
keeps using `showParagraphInViewport`, which routes through the same virtual
flow. Fallback if the wrapper misbehaves with the gutter: bind two
`ScrollBar`s to the virtual flow's estimated scroll values — contained in
`GCodeEditorView`.

### D2: Sticky doc popup anchored to the triggering word
Track the anchor (paragraph index + word range + text) captured when the
popup opens (hover or Ctrl+Q). On `MOUSE_MOVED`: if the popup is showing and
the word under the cursor is the same anchor word, keep it; otherwise hide
and restart the hover timer as today. Hide unconditionally on key press,
mouse press/click, scroll, and document text change. The Ctrl+Q path anchors
to the caret word with the same struct, so both entry points share the
dismissal policy.

### D3: Tab acceptance in the popup key handler
`onKeyPressed` gains `KeyCode.TAB` while the popup is showing: accept the
selected suggestion and `consume()` the event (prevents the tab-character
insertion). No change when the popup is hidden.

### D4: De-duplicate the key-press filter
One `KEY_PRESSED` filter handles both popup keys and Ctrl+Q; the second
registration is removed. Behavior-neutral except Enter/Escape no longer run
twice.

### D6: Go-to-line via an Edit menu and input dialog
A new **Edit** menu holds "Go to Line..." (accelerator Ctrl+G). The action
opens a small input dialog (JavaFX `TextInputDialog`, themed by Primer),
parses the value, range-checks it against the paragraph count, and calls the
existing `gotoLine(int)` (select + scroll + signal). Invalid/empty/out-of-range
input closes the dialog without moving the caret. `gotoLine` itself is
unchanged - the feature is the user-facing entry point it was missing.

### D5: Tests
TestFX: (1) set a 400-line text with a 300-character line, assert the
scene graph contains vertical and horizontal scroll bar nodes and that
goto-line(350) scrolls it into view; (2) open the doc popup via the
test hook, fire MOUSE_MOVED within the keyword → still showing, move to
another word → hidden, key press → hidden; (3) typing `G` shows the popup,
TAB event inserts the code and no `\t`, popup-closed Tab inserts nothing
special (existing behavior asserted). De-dup covered by asserting a single
filter registration effect (Enter accepts once — suggestion list unchanged
after the event).

## Risks / Trade-offs

- [VirtualizedScrollPane + LineNumberFactory interplay] → standard RichTextFX
  pairing; verified by the goto-line-through-wrapper test.
- [Sticky popup may linger over unrelated content] → dismissal on leaving
  the keyword plus key/click/text-change covers the paths; scope is one
  popup.

## Migration Plan

Presentation/interaction only; no data. Rollback = revert commit.

## Open Questions

- none blocking
