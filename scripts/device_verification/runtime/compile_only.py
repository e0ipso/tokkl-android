"""Exact compile-only plan grammars. No file reads or execution on import.

Successful controller/Session completion is not an APK identity receipt, a
benchmark pass, or a JVM or lint count. Independent artifact verification,
actual reports and root review remain needed.
"""

PROFILE = 'benchmark-apk-bootstrap-v1'
LABEL = 'benchmark-apk-bootstrap'
SECONDS = 1800
ARGS = ('./gradlew', '--no-daemon', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
        ':app:assembleBenchmark', ':macrobenchmark:assembleBenchmark', '--stacktrace')
HOST_PROFILE = 'standalone-assemble-check-v1'
HOST_LABEL = 'assemble-check'
HOST_ARGS = ('./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace')


def _exact_one_job(config, label, args, mixed, count, shape):
    if 'result_recipe' in config or 'host_recipe' in config:
        raise ValueError(mixed)
    jobs = config['plan']
    if not isinstance(jobs, list) or len(jobs) != 1:
        raise ValueError(count)
    job = jobs[0]
    expected = dict(label=label, api=0, standalone=True, seconds=SECONDS, args=list(args))
    if not (isinstance(job, dict) and type(job.get('api')) is int
            and type(job.get('seconds')) is int and job.get('standalone') is True
            and job == expected):
        raise ValueError(shape)
    return True


def validate_bootstrap_plan(config):
    """Recognize only these entire one-job plans; leave other grammars unchanged."""
    recipe = config.get('plan_recipe')
    if recipe == HOST_PROFILE:
        if 'post_input_policy' in config:
            raise ValueError('standalone assemble-check cannot carry the post-input policy')
        return _exact_one_job(
            config, HOST_LABEL, HOST_ARGS,
            'standalone assemble-check cannot mix result or host recipes',
            'standalone assemble-check requires exactly one job',
            'standalone assemble-check requires the exact canonical API0 standalone 1800-second host job')
    if recipe != PROFILE:
        return False
    return _exact_one_job(
        config, LABEL, ARGS,
        'bootstrap cannot mix result or host recipes',
        'bootstrap requires exactly one job',
        'bootstrap requires the exact canonical API0 standalone 1800-second packaging job')
