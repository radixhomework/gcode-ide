# Tasks: editor-scrollbar-docs-tab

- [x] 1.1 Wrap the CodeArea in Flowless `VirtualizedScrollPane` (node() returns the wrapper; min sizes, stylesheets, and SplitPane placement move to it); TestFX asserts vertical + horizontal scroll bar nodes exist for a long document/long line and that goto-line(350) scrolls the target into view
- [x] 1.2 Sticky documentation popup: anchor word tracking (paragraph + range), keep-open on movement within the keyword, hide on leaving the keyword / key press / click / text change; TestFX covers move-within (stays), move-away (hides), key press (hides)
- [x] 1.3 Tab acceptance in the popup key handler (accept + consume; no tab character inserted when the popup is open); TestFX asserts Tab inserts the selected code and the text contains no `\t`, and that Tab with no popup is unchanged
- [x] 1.4 De-duplicate the KEY_PRESSED filter registration (Enter/Escape handled once)
- [x] 1.6 Implement Edit > "Go to Line..." (Ctrl+G) with an input dialog calling `gotoLine` (invalid/out-of-range input ignored); TestFX test fires the action with 42 in a 100-line document (caret at 42) and with `abc` (caret unchanged)
- [x] 1.5 README note (scroll bars, sticky docs, Tab completion); full `mvn test` green; rebuild the app image and verify by hand: scroll bars on a long file, sticky popup, Tab completion
