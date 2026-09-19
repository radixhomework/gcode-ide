"""MachineProfile validation and YAML round-trip (task 2.1)."""

import pytest

from app.profiles.profile import MachineProfile, ProfileError

VALID = {
    "name": "Test Mill",
    "bed": {"x": 300.0, "y": 180.0, "z": 45.0},
    "feeds": {"max_cut": 800.0, "max_rapid": 1000.0},
    "safe_z": 5.0,
}


def _mutate(**overrides):
    data = {
        "name": VALID["name"],
        "bed": dict(VALID["bed"]),
        "feeds": dict(VALID["feeds"]),
        "safe_z": VALID["safe_z"],
    }
    for dotted, value in overrides.items():
        section, _, key = dotted.partition(".")
        if not key:
            data[section] = value
        else:
            data[section][key] = value
    return data


def test_valid_profile_loads():
    profile = MachineProfile.from_dict(_mutate())
    assert profile.name == "Test Mill"
    assert (profile.bed_x, profile.bed_y, profile.bed_z) == (300.0, 180.0, 45.0)
    assert (profile.max_cut, profile.max_rapid, profile.safe_z) == (800.0, 1000.0, 5.0)


@pytest.mark.parametrize(
    "field,value",
    [
        ("bed.x", 0),
        ("bed.y", -10),
        ("bed.z", "wide"),
        ("feeds.max_cut", 0),
        ("feeds.max_rapid", -1),
    ],
)
def test_invalid_field_rejected_with_name(field, value):
    with pytest.raises(ProfileError) as excinfo:
        MachineProfile.from_dict(_mutate(**{field: value}))
    assert field in str(excinfo.value)


def test_missing_field_rejected_with_name():
    data = _mutate()
    del data["feeds"]["max_rapid"]
    with pytest.raises(ProfileError) as excinfo:
        MachineProfile.from_dict(data)
    assert "feeds.max_rapid" in str(excinfo.value)


def test_yaml_round_trip():
    original = MachineProfile.from_dict(_mutate())
    restored = MachineProfile.from_yaml(original.to_yaml())
    assert restored == original
