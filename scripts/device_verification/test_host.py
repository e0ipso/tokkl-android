#!/usr/bin/env python3
"""Pure host fixtures. No live systemd, Gradle, adb, emulator or cgroup writes."""
from pathlib import Path
import os
import sys
import tempfile
import unittest

base = Path(__file__).resolve().parent
sys.path[:0] = [str(base), str(base/'runtime'), str(base/'tests')]
# Subprocess holder fixtures import the SAME maintained module.
os.environ['PYTHONPATH'] = str(base/'runtime')
cache = Path.home()/'.cache/tokkl-controller-host-tests'
cache.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=cache) as scratch:
    os.environ['TOKKL_HOST_FIXTURE_ROOT'] = scratch
    tempfile.tempdir = scratch
    # Legacy fixtures created their temporary directories next to their source.
    # Point their BASE only at owned disk scratch; implementation imports stay
    # on the maintained runtime. Fixture holder needs a local copy of its script.
    import shutil
    shutil.copy2(base/'tests/fixture_holder.py', Path(scratch)/'fixture_holder.py')
    import test_scope_completion
    test_scope_completion.BASE = Path(scratch)
    import test_consistent_events
    test_consistent_events.BASE = base/'tests/fixtures'
    suite = unittest.defaultTestLoader.loadTestsFromNames([
        'test_scope_completion', 'test_event_monitor', 'test_consistent_events', 'test_candidate'])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(not result.wasSuccessful())
