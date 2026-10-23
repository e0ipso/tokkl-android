"""Exact packaging-only plan grammar. No file reads or execution on import.

Successful controller/Session completion is not an APK identity receipt or a
benchmark pass. Independent artifact verification and root review remain needed.
"""

PROFILE = 'benchmark-apk-bootstrap-v1'
LABEL = 'benchmark-apk-bootstrap'
SECONDS = 1800
ARGS = ('./gradlew', '--no-daemon', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
        ':app:assembleBenchmark', ':macrobenchmark:assembleBenchmark', '--stacktrace')


def validate_bootstrap_plan(config):
    """Recognize only this entire one-job plan; leave other grammars unchanged."""
    if config.get('plan_recipe') != PROFILE:
        return False
    if 'result_recipe' in config or 'host_recipe' in config:
        raise ValueError('bootstrap cannot mix result or host recipes')
    jobs = config['plan']
    if not isinstance(jobs, list) or len(jobs) != 1:
        raise ValueError('bootstrap requires exactly one job')
    job = jobs[0]
    expected = dict(label=LABEL, api=0, standalone=True, seconds=SECONDS, args=list(ARGS))
    if not (isinstance(job, dict) and type(job.get('api')) is int
            and type(job.get('seconds')) is int and job.get('standalone') is True
            and job == expected):
        raise ValueError('bootstrap requires the exact canonical API0 standalone 1800-second packaging job')
    return True
