"""Capped host validation of a fixture snapshot, stopping before resource work.

The controller and helper entry points are real. Their first post-validation
boundary is intercepted; no sysfs, lock, write intent, scope or payload is used.
"""
import builtins
import json
import os
from pathlib import Path
import resource
import runpy
import sys
from unittest.mock import patch


def main():
    directory, adoption, fixture, operation = sys.argv[1:]
    directory = Path(directory)
    sys.path.insert(0, str(directory))
    import configuration
    import preparation_policy

    limit = 64 * 1024**2
    resource.setrlimit(resource.RLIMIT_AS, (limit, limit))
    result = {'operation': operation, 'address_space_limit_bytes': limit,
              'fixture_bytes': Path(fixture).stat().st_size,
              'resource_actions': False}

    class Boundary(Exception):
        pass

    original_import, original_open = builtins.__import__, builtins.open

    def import_boundary(name, *args, **kwargs):
        if name == 'capture_evidence':
            raise Boundary('controller validated; stopped before runtime/resource setup')
        return original_import(name, *args, **kwargs)

    def open_boundary(path, *args, **kwargs):
        if str(path) == '/run/user/1000/tokkl-android-mem-gate.lock':
            raise Boundary('parent validated; stopped before admission lock/resource reads')
        return original_open(path, *args, **kwargs)

    def intent_boundary(request):
        result['validated_amount'] = request['amount']
        raise Boundary('writer validated; stopped before durable intent/reclaim')

    try:
        if operation == 'sha':
            result['digest'] = configuration.sha(fixture)
        elif operation == 'adopted':
            result['configuration_sha256'] = configuration.adopted_configuration(directory, adoption)['_sha256']
        elif operation == 'controller':
            sys.argv = [str(directory/'controller.py'), '--execute', '--adoption', adoption]
            with patch('builtins.__import__', import_boundary):
                runpy.run_path(sys.argv[0], run_name='__main__')
            raise AssertionError('controller must stop at the modeled boundary')
        elif operation in ['parent', 'writer']:
            os.environ['TOKKL_VERIFICATION_ADOPTION'] = adoption
            request = json.loads((directory/'host-probe-request.json').read_text())
            sys.argv = [str(directory/'local-reclaim-finite.py')]
            if operation == 'writer':
                sys.argv.append('--write')
            sys.argv += request
            with patch('builtins.open', open_boundary), patch.object(preparation_policy, 'reserve_write', intent_boundary):
                runpy.run_path(sys.argv[0], run_name='__main__')
            raise AssertionError('helper must stop at the modeled boundary')
        else:
            raise ValueError('unknown host probe operation')
        result['status'] = 'validated'
    except Boundary as stop:
        result.update(status='validated', boundary=str(stop))
    except Exception as error:
        result.update(status='error', error=type(error).__name__, message=str(error))
    status = dict(line.split(':', 1) for line in Path('/proc/self/status').read_text().splitlines() if ':' in line)
    # Linux ru_maxrss may include the fork/pre-exec parent's high-water mark.
    # VmHWM belongs to this exec's memory map; record both rather than attributing
    # inherited accounting to this validation. Neither measures cgroup/page cache.
    result['peak_rss_bytes'] = int(status['VmHWM'].split()[0]) * 1024
    result['getrusage_maxrss_bytes'] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * 1024
    print(json.dumps(result, sort_keys=True), flush=True)
    return 0 if result['status'] == 'validated' else 42


if __name__ == '__main__':
    sys.exit(main())
