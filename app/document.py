"""Document state (path, modified flag) and G-code file IO."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

GCODE_NAME_FILTER = "G-code files (*.nc *.gcode *.ngc *.tap);;All files (*)"


@dataclass
class Document:
    """The G-code document being edited: its file path and modified flag."""

    path: Path | None = None
    modified: bool = False

    @property
    def display_name(self) -> str:
        return self.path.name if self.path is not None else "untitled.nc"


def read_document(path: Path | str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write_document(path: Path | str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")
