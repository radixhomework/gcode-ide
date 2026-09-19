"""Profile loading: built-in assets plus a user directory (pure Python, no Qt).

Callers pass directories in; the Qt-specific default user directory lives in
the thin wrapper `qt_paths` so this module stays importable without a GUI.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from .profile import MachineProfile, ProfileError

PROFILE_SUFFIXES = {".yaml", ".yml"}


@dataclass
class LoadedProfiles:
    """Profiles that loaded, plus one diagnostic per skipped file."""

    profiles: list[MachineProfile] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def load_profiles(builtin_dir: Path | str, user_dir: Path | str | None = None) -> LoadedProfiles:
    """Load built-in profiles, then any user-supplied ones.

    A malformed file is skipped with a diagnostic naming the file and the
    reason; other files still load.
    """
    result = LoadedProfiles()
    for directory in (builtin_dir, user_dir):
        if directory is None:
            continue
        for path in sorted(Path(directory).glob("*")):
            if path.suffix.lower() not in PROFILE_SUFFIXES:
                continue
            try:
                result.profiles.append(MachineProfile.from_yaml(path.read_text(encoding="utf-8")))
            except (ProfileError, OSError) as exc:
                result.errors.append(f"{path.name}: {exc}")
    return result
