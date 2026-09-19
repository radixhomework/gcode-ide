"""Built-in preset contents and loader behavior (tasks 2.2–2.3)."""

from pathlib import Path

from app.profiles.loader import load_profiles
from app.profiles.profile import MachineProfile
from app.resources import PROFILES_DIR


def test_builtin_preset_loads_with_all_documented_fields():
    path = Path(PROFILES_DIR) / "prover_3018.yaml"
    profile = MachineProfile.from_yaml(path.read_text(encoding="utf-8"))
    assert profile.name == "PROVER 3018"
    assert (profile.bed_x, profile.bed_y, profile.bed_z) == (300.0, 180.0, 45.0)
    assert profile.max_cut == 800.0
    assert profile.max_rapid == 1000.0
    assert profile.safe_z == 5.0


def test_loader_finds_builtin_preset(tmp_path):
    loaded = load_profiles(PROFILES_DIR, None)
    assert any(p.name == "PROVER 3018" for p in loaded.profiles)
    assert loaded.errors == []


def test_loader_picks_up_valid_user_profile(tmp_path):
    (tmp_path / "custom.yaml").write_text(
        "name: Custom Mill\n"
        "bed: { x: 200.0, y: 200.0, z: 50.0 }\n"
        "feeds: { max_cut: 500.0, max_rapid: 900.0 }\n"
        "safe_z: 2.5\n",
        encoding="utf-8",
    )
    loaded = load_profiles(PROFILES_DIR, tmp_path)
    names = [p.name for p in loaded.profiles]
    assert "Custom Mill" in names
    assert "PROVER 3018" in names


def test_loader_skips_malformed_profile_but_loads_others(tmp_path):
    (tmp_path / "broken.yaml").write_text("name: Broken\nbed: { x: 0, y: 100, z: 50 }\n", encoding="utf-8")
    (tmp_path / "good.yaml").write_text(
        "name: Good Mill\n"
        "bed: { x: 100.0, y: 100.0, z: 30.0 }\n"
        "feeds: { max_cut: 400.0, max_rapid: 600.0 }\n"
        "safe_z: 1.0\n",
        encoding="utf-8",
    )
    loaded = load_profiles(None, tmp_path)
    names = [p.name for p in loaded.profiles]
    assert "Good Mill" in names
    assert "Broken" not in names
    assert len(loaded.errors) == 1
    assert "broken.yaml" in loaded.errors[0]
    assert "bed.x" in loaded.errors[0]


def test_loader_ignores_non_yaml_files(tmp_path):
    (tmp_path / "notes.txt").write_text("not a profile", encoding="utf-8")
    loaded = load_profiles(None, tmp_path)
    assert loaded.profiles == []
    assert loaded.errors == []
