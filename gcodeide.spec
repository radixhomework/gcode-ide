# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec: one-file build bundling the app and its assets.

Mirrors grblmc.spec. Build with `pyinstaller gcodeide.spec`; the binary lands
in dist/. Machine profiles under the user's config directory are read at
runtime and are not part of the bundle.
"""

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=[],
    datas=[("assets", "assets")],
    hiddenimports=[],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="gcode-ide",
    debug=False,
    strip=False,
    upx=False,
    console=False,  # GUI app: no console window
)
