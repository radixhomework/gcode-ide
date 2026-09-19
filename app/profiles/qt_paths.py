"""Thin Qt wrapper: default user directories via QStandardPaths.

Kept separate from the pure-Python loader so tests never need a QApplication.
"""

from __future__ import annotations

from pathlib import Path

APP_DIR_NAME = "gcode-ide"


def _app_config_location() -> str:
    from PySide6.QtCore import QStandardPaths

    return QStandardPaths.writableLocation(QStandardPaths.StandardLocation.AppConfigLocation)


def user_profiles_dir(create: bool = False) -> Path:
    """Per-user profile directory: AppConfigLocation/gcode-ide/profiles."""
    path = Path(_app_config_location()) / APP_DIR_NAME / "profiles"
    if create:
        path.mkdir(parents=True, exist_ok=True)
    return path


def app_config_file() -> Path:
    """App config file: AppConfigLocation/gcode-ide/config.yaml."""
    return Path(_app_config_location()) / APP_DIR_NAME / "config.yaml"
