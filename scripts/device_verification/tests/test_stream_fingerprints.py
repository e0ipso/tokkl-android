"""Actual bounded fingerprint/adoption paths. Disk fixtures and host children only."""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from test_candidate import PACKAGE, SCRATCH, config
from runtime import configuration as cfg
from prepare import prepare

BUFFER = 64 * 1024
LARGE_BYTES = 96 * 1024**2 + 17


class StreamingFingerprintTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_empty_chunk_boundaries_and_multiple_chunks_match_byte_digests(self):
        path = self.root/'input'
        for size in [0, 1, BUFFER-1, BUFFER, BUFFER+1, 3*BUFFER+17]:
            with self.subTest(size=size):
                data = (bytes(range(256)) * ((size+255)//256))[:size]
                path.write_bytes(data)
                self.assertEqual(cfg.sha(path), hashlib.sha256(data).hexdigest())

    def test_single_fixed_buffer_and_read_failures_propagate(self):
        reads = []
        class Reader(io.BytesIO):
            def read(self, *args):
                raise AssertionError('whole-file/allocating read is forbidden')
            def readinto(self, buffer):
                reads.append((id(buffer), len(buffer)))
                return super().readinto(buffer)
        data = b'abc' * BUFFER
        with patch.object(Path, 'open', return_value=Reader(data)):
            self.assertEqual(cfg.sha(self.root/'modeled-read'), hashlib.sha256(data).hexdigest())
        self.assertEqual(len(set(reads)), 1)
        self.assertEqual(reads[0][1], BUFFER)
        self.assertGreater(len(reads), 2)
        class Broken(Reader):
            def readinto(self, buffer):
                raise OSError('fixture read failure')
        with patch.object(Path, 'open', return_value=Broken(data)), self.assertRaisesRegex(OSError, 'fixture read failure'):
            cfg.sha(self.root/'modeled-error')
        with self.assertRaises(FileNotFoundError):
            cfg.sha(self.root/'missing')
        with self.assertRaises(IsADirectoryError):
            cfg.sha(self.root)

    def frozen(self):
        fixture = self.root/'large-sparse-input'
        with fixture.open('wb') as out:
            out.truncate(LARGE_BYTES)
        # Independent system SHA, never a whole-file Python allocation.
        reference = subprocess.check_output(['sha256sum', str(fixture)], text=True).split()[0]
        candidate = config(self.root)
        candidate['input_pins'][str(fixture)] = reference
        candidate.update(reclaim_budget_bytes=6*1024**3,
                         preparation_policy={'max_request_bytes': 4*1024**3, 'max_preparations': 3})
        proof = prepare(candidate)
        run = Path(proof['run_dir'])
        adoption = self.root/'HOST-ONLY-adoption.json'
        adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=candidate['lease_id'],
            approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True)))
        amount = 256*1024**2
        state_path = run/'window-state-host.json'
        argv = [str(amount), '--state', str(state_path), '--preparation', '0']
        state_path.write_text(json.dumps(dict(phase='prepared', configuration_sha256=proof['configuration_sha256'],
            project=candidate['project'], run_id=candidate['run_id'], lease_id=candidate['lease_id'],
            reclaim_budget_bytes=candidate['reclaim_budget_bytes'], reclaim_reserved_bytes=amount,
            preparations=[dict(label='full36', requested=amount, write_count=1, deficit=amount-134217728,
                before={'stat': {'inactive_file': amount}},
                command=['/usr/bin/python3', str(run/'local-reclaim-finite.py')]+argv)])))
        (run/'host-probe-request.json').write_text(json.dumps(argv))
        return fixture, reference, candidate, proof, run, adoption

    def probe(self, run, adoption, fixture, operation):
        result = subprocess.run([sys.executable, '-B', str(PACKAGE/'tests/hash_memory_probe.py'),
            str(run), str(adoption), str(fixture), operation], capture_output=True, text=True, timeout=15)
        print('HASH_PROBE '+result.stdout.strip(), flush=True)
        return result

    def test_capped_actual_hash_adoption_controller_parent_and_writer(self):
        fixture, reference, candidate, proof, run, adoption = self.frozen()
        for operation in ['sha', 'adopted', 'controller', 'parent', 'writer']:
            with self.subTest(operation=operation):
                result = self.probe(run, adoption, fixture, operation)
                self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
                receipt = json.loads(result.stdout)
                self.assertEqual(receipt['status'], 'validated')
                self.assertFalse(receipt['resource_actions'])
                self.assertLess(receipt['peak_rss_bytes'], receipt['address_space_limit_bytes'])
                self.assertGreater(receipt['fixture_bytes'], receipt['address_space_limit_bytes'])
                if operation == 'sha': self.assertEqual(receipt['digest'], reference)
                if operation == 'adopted': self.assertEqual(receipt['configuration_sha256'], proof['configuration_sha256'])
        self.assertFalse(list(run.glob('preparation-write-*')))
        self.assertFalse(list(run.glob('*-plan-once.json')))

    def test_original_whole_file_validator_fails_all_capped_entry_points(self):
        fixture, reference, candidate, proof, run, adoption = self.frozen()
        # Exact original implementation, in a NEW owned host fixture only.
        original = subprocess.check_output(['git', 'show',
            'ec5f580:scripts/device_verification/runtime/configuration.py'], cwd=PACKAGE)
        (run/'configuration.py').write_bytes(original)
        manifest_path = run/'runtime-manifest.json'
        manifest = json.loads(manifest_path.read_text())
        manifest['configuration.py'] = hashlib.sha256(original).hexdigest()
        manifest_path.write_bytes(cfg.canonical(manifest))
        row = json.loads(adoption.read_text())
        row['runtime_sha256'] = hashlib.sha256(cfg.canonical(manifest)).hexdigest()
        adoption.write_text(json.dumps(row))
        print('ORIGINAL_CONFIGURATION_SHA256 '+manifest['configuration.py'], flush=True)
        for operation in ['sha', 'adopted', 'controller', 'parent', 'writer']:
            result = self.probe(run, adoption, fixture, operation)
            self.assertEqual(result.returncode, 42, result.stdout+result.stderr)
            self.assertEqual(json.loads(result.stdout)['error'], 'MemoryError')
        self.assertFalse(list(run.glob('preparation-write-*')))
        self.assertFalse(list(run.glob('*-plan-once.json')))

    def test_large_pin_wrong_digest_mutation_missing_runtime_and_adoption_refuse(self):
        fixture, reference, candidate, proof, run, adoption = self.frozen()
        wrong = copy.deepcopy(candidate)
        wrong['input_pins'][str(fixture)] = '0'*64
        with self.assertRaisesRegex(ValueError, 'checked input changed'):
            cfg.validate(wrong, fresh=False)
        with fixture.open('r+b') as out:
            out.seek(BUFFER+1);out.write(b'changed')
        for operation in ['adopted', 'controller', 'parent', 'writer']:
            result = self.probe(run, adoption, fixture, operation)
            self.assertEqual(result.returncode, 42)
            self.assertEqual(json.loads(result.stdout)['error'], 'ValueError')
            self.assertIn('checked input changed', result.stdout)
        fixture.unlink()
        with self.assertRaises(FileNotFoundError):
            cfg.adopted_configuration(run, adoption)
        with fixture.open('wb') as out: out.truncate(LARGE_BYTES)
        original = (run/'configuration.py').read_bytes()
        (run/'configuration.py').write_bytes(original+b'\n# tampered\n')
        with self.assertRaisesRegex(ValueError, 'frozen runtime changed'):
            cfg.adopted_configuration(run, adoption)
        (run/'configuration.py').write_bytes(original)
        row = json.loads(adoption.read_text());row['lease_id'] = 'wrong-lease'
        adoption.write_text(json.dumps(row))
        with self.assertRaisesRegex(ValueError, 'coordinator adoption'):
            cfg.adopted_configuration(run, adoption)
        self.assertFalse(list(run.glob('preparation-write-*')))

    def test_carried_phase_three_job_configuration_preserves_spent_reservation(self):
        ledger = self.root/'previous-host-ledger.json'
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 2189529088}))
        candidate = config(self.root)
        candidate.update(reclaim_budget_bytes=6442450944, carried_reclaim_bytes=2189529088,
            preparation_policy={'max_request_bytes': 4294967296, 'max_preparations': 3},
            prior_window=str(ledger), prior_window_sha256=cfg.sha(ledger))
        candidate['plan'] += [
            dict(api=0, label='assemble-check', standalone=True, seconds=1800,
                 args=['./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace']),
            dict(api=0, label='reproducible-release', standalone=True, seconds=1800,
                 args=['env', 'GRADLE_OPTS=-Dorg.gradle.workers.max=1', 'scripts/reproducible-release.sh'])]
        checked = cfg.validate(candidate)
        self.assertEqual(checked['reclaim_budget_bytes']-checked['carried_reclaim_bytes'], 4252921856)
        self.assertEqual(len(checked['plan']), checked['preparation_policy']['max_preparations'])
        candidate['carried_reclaim_bytes'] = 0
        with self.assertRaisesRegex(ValueError, 'never reset'):
            cfg.validate(candidate)
        candidate['carried_reclaim_bytes'] = 2189529088
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 0}))
        with self.assertRaisesRegex(ValueError, 'prior ledger changed'):
            cfg.validate(candidate)


if __name__ == '__main__':
    unittest.main(verbosity=2)
