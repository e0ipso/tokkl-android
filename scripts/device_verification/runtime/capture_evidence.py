"""Bounded immutable raw-frame copies and failure-only durable diagnostics.

This observer never classifies evidence, resamples, changes an anchor or clears
an exception. The actual reader/Session/Monitor remain authoritative.
"""
from collections import deque
import io
import json
import os
from pathlib import Path
import sys
import time
import uuid

FRAME_COUNT = 5
FRAME_BYTES = 256 * 1024
CONTEXT_BYTES = 512 * 1024
FILE_BYTES = FRAME_COUNT * FRAME_BYTES + CONTEXT_BYTES + 4096
FAILURE_FILES = 4
_fsync = os.fsync


def bounded(value, limit):
    output = io.BytesIO()
    try:
        for chunk in json.JSONEncoder(ensure_ascii=True, separators=(',', ':'), allow_nan=False).iterencode(value):
            if len(chunk) > limit-output.tell():
                raise ValueError('diagnostic byte limit exceeded')
            output.write(chunk.encode('ascii'))
        return output.getvalue()
    except Exception as error:
        return json.dumps({'diagnostic_gap': type(error).__name__+': '+str(error)[:256]}, separators=(',', ':')).encode()


class CaptureEvidence:
    def __init__(self, reader, directory):
        self.reader = reader
        self.directory = Path(directory)
        self.frames = deque(maxlen=FRAME_COUNT)
        self.sequence = 0
        self.diagnostic_error = None
        self.failure_files = 0

    def memory(self):
        return {'frames': len(self.frames), 'serialized_bytes': sum(map(len, self.frames)),
                'retained_container_and_bytes_objects': sys.getsizeof(self.frames)+sum(map(sys.getsizeof, self.frames)),
                'max_frames': FRAME_COUNT, 'max_frame_bytes': FRAME_BYTES,
                'max_retained_payload_bytes': FRAME_COUNT*FRAME_BYTES,
                'representation': 'immutable serialized copies; no retained raw-tree references'}

    def _record(self, record):
        self.sequence += 1
        record['sequence'] = self.sequence
        try:
            self.frames.append(bounded(record, FRAME_BYTES))
        except Exception as error:
            self.diagnostic_error = repr(error)[:1024]

    def __call__(self, *args, **kwargs):
        start = time.monotonic()
        record = {'started_monotonic': start, 'root_argument': str(kwargs.get('root', args[0] if args else '<reader default>')),
                  'deadline': kwargs.get('deadline')}
        try:
            tree = self.reader(*args, **kwargs)
        except BaseException as error:
            record.update(finished_monotonic=time.monotonic(), exception=repr(error)[:1024])
            self._record(record)
            self.persist('reader exception', {'original_exception': repr(error)[:1024]})
            raise
        record.update(finished_monotonic=time.monotonic(), tree=tree)
        self._record(record)
        return tree

    def persist(self, reason, context):
        """Best effort diagnostics only. A write error never replaces an abort."""
        if self.failure_files >= FAILURE_FILES:
            return {'durable': False, 'diagnostic_error': 'finite diagnostic file allowance exhausted'}
        self.failure_files += 1
        target = self.directory/(uuid.uuid4().hex+'.json')
        try:
            self.directory.mkdir(mode=0o700, parents=False, exist_ok=True)
            if self.directory.is_symlink() or self.directory.stat().st_uid != os.getuid() or self.directory.stat().st_mode & 0o777 != 0o700:
                raise OSError('unsafe owned diagnostic directory')
            header = bounded({'schema_version': 1, 'reason': str(reason)[:1024], 'buffer': self.memory(),
                              'diagnostic_error': self.diagnostic_error}, 4000)
            detail = bounded(context, CONTEXT_BYTES)
            with target.open('xb') as output:
                os.chmod(target, 0o600)
                output.write(b'{"header":'+header+b',"raw_captures":[')
                for index, frame in enumerate(self.frames):
                    if index: output.write(b',')
                    output.write(frame)
                output.write(b'],"context":'+detail+b'}\n')
                output.flush()
                if output.tell() > FILE_BYTES:
                    raise OSError('diagnostic file byte bound exceeded')
                _fsync(output.fileno())
            for directory in [self.directory, self.directory.parent]:
                fd = os.open(directory, os.O_DIRECTORY)
                try: _fsync(fd)
                finally: os.close(fd)
            return {'path': str(target), 'bytes': target.stat().st_size, 'durable': True}
        except Exception as error:
            return {'path': str(target), 'durable': False, 'diagnostic_error': repr(error)[:1024]}
