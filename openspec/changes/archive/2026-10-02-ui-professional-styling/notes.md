# Notes

- AtlantaFX adopted during review (maintainer request): `atlantafx-base`
  3.0.0 supplies the modern flat component base (Primer Light) via
  `ThemeManager.setTheme(...)`; the `app.css` token layer stays as the scene
  stylesheet (higher precedence than UA), so token-driven components
  (status bar, chips, popovers, editor palette) keep their look while
  buttons, combo boxes, text fields, and menus gain Primer's flat styling.
  This adds the change's first runtime dependency — proposal "no new
  dependencies" is amended accordingly.
- Divider fix: SubScene's default minimum size equals its current bounds,
  which froze the SplitPane (preview could grow but never shrink); the
  wrapper min sizes are zeroed and regression-tested.
- Viewport token `-gd-viewport` (#e3e7ee) darkens the 3D backdrop so the
  bed plane reads; menu bar and context menus restyled flat over Primer.
