"""App configuration (active profile, window geometry) as a small YAML file.

Stored under the Qt per-user config directory (sibling of the user profiles
directory, design D8) — mirroring the controller's config.yaml style.
"""

from __future__ import annotations

from pathlib import Path

import yaml


def load_config(path: Path | str | None) -> dict:
    if path is None:
        return {}
    try:
        data = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError):
        return {}
    return data if isinstance(data, dict) else {}


def save_config(path: Path | str, data: dict) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(yaml.safe_dump(data, sort_keys=False), encoding="utf-8")
