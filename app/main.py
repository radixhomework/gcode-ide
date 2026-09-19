"""Application entry point.

Usage:
  gcode-ide                # start the IDE
  python -m app.main       # same, via module
"""

from __future__ import annotations

import argparse
import sys

from . import __version__


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else list(argv)
    if "--version" in args:
        # handled before argparse: a GUI-subsystem binary has no stdout
        if sys.stdout is not None:
            print(f"gcode-ide {__version__}")
        return 0

    parser = argparse.ArgumentParser(prog="gcode-ide", description="Desktop IDE for GRBL G-code")
    parser.parse_args(args)

    from PySide6.QtWidgets import QApplication

    from .main_window import MainWindow

    app = QApplication(args)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
