import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("discovery", Path(__file__).resolve().parents[1] / "scripts/emulator-discovery.py")
discovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(discovery)


class DiscoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.runtime = self.root / "shared"
        self.runtime.mkdir(mode=0o700)
        self.work = self.root / "run"
        self.work.mkdir(mode=0o700)
        self.source_dir = self.work / "runtime/avd/running"
        self.source_dir.mkdir(parents=True, mode=0o700)
        self.source = self.source_dir / f"pid_{os.getpid()}.ini"
        self.source.write_text("port.serial=5554\ngrpc.port=8554\ngrpc.token=test-placeholder\n")
        self.source.chmod(0o600)
        self.link = self.runtime / "avd/running" / self.source.name

    def test_authenticated_record_is_discoverable_without_copying_or_changing_permissions(self):
        discovery.publish(self.work, self.runtime, "emulator-5554")
        self.assertTrue(self.link.is_symlink())
        self.assertEqual(self.source, self.link.resolve())
        self.assertEqual(0o600, self.source.stat().st_mode & 0o777)
        self.assertEqual(0o700, self.link.parent.stat().st_mode & 0o777)
        discovery.publish(self.work, self.runtime, "emulator-5554")
        discovery.remove(self.work, self.runtime)
        self.assertFalse(self.link.is_symlink())
        self.assertTrue(self.source.exists())

    def test_existing_unrelated_file_is_never_overwritten_or_removed(self):
        self.link.parent.parent.mkdir(mode=0o700)
        self.link.parent.mkdir(mode=0o700)
        self.link.write_text("unrelated")
        with self.assertRaises(FileExistsError):
            discovery.publish(self.work, self.runtime, "emulator-5554")
        discovery.remove(self.work, self.runtime)
        self.assertEqual("unrelated", self.link.read_text())

    def test_cleanup_removes_only_this_runs_links_even_after_source_disappears(self):
        discovery.publish(self.work, self.runtime, "emulator-5554")
        other = self.link.parent / "pid_999999.ini"
        other.symlink_to(self.root / "other-run/runtime/avd/running/pid_999999.ini")
        self.source.unlink()
        discovery.remove(self.work, self.runtime)
        self.assertFalse(self.link.is_symlink())
        self.assertTrue(other.is_symlink())

    def test_wrong_serial_and_unsafe_permissions_are_rejected(self):
        with self.assertRaises(ValueError):
            discovery.publish(self.work, self.runtime, "emulator-5556")
        self.work.chmod(0o755)
        with self.assertRaises(PermissionError):
            discovery.publish(self.work, self.runtime, "emulator-5554")
        self.assertFalse(self.link.is_symlink())

    def test_redirected_shared_directory_is_rejected(self):
        elsewhere = self.root / "elsewhere"
        elsewhere.mkdir()
        (self.runtime / "avd").symlink_to(elsewhere)
        with self.assertRaises(PermissionError):
            discovery.publish(self.work, self.runtime, "emulator-5554")


if __name__ == "__main__":
    unittest.main()
