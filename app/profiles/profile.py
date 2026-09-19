"""Machine profile: bed envelope, feed limits, safe Z (pure Python, no Qt).

The YAML schema is an interchange contract shared with grbl-machine-controller
(see design D8): always millimeters, flat documented field names, additive-only
evolution::

    name: PROVER 3018
    bed: { x: 300.0, y: 180.0, z: 45.0 }
    feeds:
      max_cut: 800.0
      max_rapid: 1000.0
    safe_z: 5.0
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import yaml


class ProfileError(ValueError):
    """Raised when a profile is invalid; the message names the offending field."""


@dataclass
class MachineProfile:
    """A machine definition in millimeters and mm/min."""

    name: str
    bed_x: float
    bed_y: float
    bed_z: float
    max_cut: float
    max_rapid: float
    safe_z: float

    @classmethod
    def from_dict(cls, data: dict) -> MachineProfile:
        """Build a profile from a YAML mapping, validating as required fields are read."""
        if not isinstance(data, dict):
            raise ProfileError("profile must be a YAML mapping")

        name = data.get("name")
        if not isinstance(name, str) or not name.strip():
            raise ProfileError("name must be a non-empty string")

        bed = data.get("bed")
        if not isinstance(bed, dict):
            raise ProfileError("bed must be a mapping with x, y, z extents")
        bed_x = _positive(bed, "x", "bed")
        bed_y = _positive(bed, "y", "bed")
        bed_z = _positive(bed, "z", "bed")

        feeds = data.get("feeds")
        if not isinstance(feeds, dict):
            raise ProfileError("feeds must be a mapping with max_cut, max_rapid")
        max_cut = _positive(feeds, "max_cut", "feeds")
        max_rapid = _positive(feeds, "max_rapid", "feeds")

        safe_z = data.get("safe_z")
        if not _is_number(safe_z):
            raise ProfileError("safe_z must be a number")
        if safe_z < 0:
            raise ProfileError(f"safe_z must be non-negative, got {safe_z}")

        return cls(
            name=name,
            bed_x=bed_x,
            bed_y=bed_y,
            bed_z=bed_z,
            max_cut=max_cut,
            max_rapid=max_rapid,
            safe_z=float(safe_z),
        )

    def to_dict(self) -> dict:
        """Serialize to the documented YAML mapping (round-trips through from_dict)."""
        return {
            "name": self.name,
            "bed": {"x": self.bed_x, "y": self.bed_y, "z": self.bed_z},
            "feeds": {"max_cut": self.max_cut, "max_rapid": self.max_rapid},
            "safe_z": self.safe_z,
        }

    @classmethod
    def from_yaml(cls, text: str) -> MachineProfile:
        """Parse and validate a profile from YAML text."""
        try:
            data = yaml.safe_load(text)
        except yaml.YAMLError as exc:
            raise ProfileError(f"invalid YAML: {exc}") from exc
        return cls.from_dict(data)

    def to_yaml(self) -> str:
        """Serialize the profile as YAML text."""
        return yaml.safe_dump(self.to_dict(), sort_keys=False)


def _is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _positive(mapping: dict, key: str, prefix: str) -> float:
    if key not in mapping:
        raise ProfileError(f"missing required field: {prefix}.{key}")
    value = mapping[key]
    if not _is_number(value):
        raise ProfileError(f"{prefix}.{key} must be a number, got {value!r}")
    if value <= 0:
        raise ProfileError(f"{prefix}.{key} must be positive, got {value}")
    return float(value)
