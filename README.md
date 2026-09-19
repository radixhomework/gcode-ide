# G-Code IDE

A small desktop IDE for GRBL G-code: hand-edit programs with syntax coloring,
preview the toolpath against the machine bed, and — in later phases — generate
code from SVG drawings, text, and images. Built for a Sainsmart PROVER 3018;
machine control (jogging, streaming, probing) lives in the sibling project
[grbl-machine-controller](https://github.com/radixhomework/grbl-machine-controller).

## Quickstart

Requires Python 3.9+.

```
python -m venv .venv
.venv\Scripts\activate            # Windows (else: source .venv/bin/activate)
pip install -e ".[dev]"
gcode-ide                         # or: python -m app.main
```

Open `assets/examples/demo.nc` from the app to see editing, live preview,
statistics, and out-of-bed warnings working together.

## Machine profiles

Profiles are plain YAML (millimeters, mm/min) in `assets/profiles/` — the
PROVER 3018 preset ships built-in — plus any files you drop into your user
config directory (`gcode-ide/profiles` next to the app config). The same
format is designed to be read by `grbl-machine-controller` without shared code.

```yaml
name: PROVER 3018
bed: { x: 300.0, y: 180.0, z: 45.0 }
feeds:
  max_cut: 800.0
  max_rapid: 1000.0
safe_z: 5.0
```

## Development

```
pytest                    # pure-core + pytest-qt GUI tests
ruff check .              # lint
pyinstaller gcodeide.spec # standalone binary in dist/
```

Architecture: all domain logic (parsing, profiles, statistics/warnings) is
pure Python with no Qt imports; Qt (PySide6) lives only in the editor,
preview, and main-window edge. See `openspec/` for specifications.
