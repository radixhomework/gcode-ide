"""Filesystem resource paths (assets shipped with the application)."""

import os

ASSETS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets")
PROFILES_DIR = os.path.join(ASSETS_DIR, "profiles")
EXAMPLES_DIR = os.path.join(ASSETS_DIR, "examples")
