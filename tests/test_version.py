"""Version smoke test."""

import app


def test_version_is_defined():
    assert isinstance(app.__version__, str)
    assert app.__version__
