
- Review fix: the editor's background, default text fill, and line-number
  gutter kept their light paint because only the syntax spans were
  palette-driven. editor.css now styles `.styled-text-area`,
  `.styled-text-area .text` (default fill), and `.lineno` with the shared
  tokens, so the whole editor surface follows the theme.
