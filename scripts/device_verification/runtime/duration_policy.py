"""Finite operational deadlines; no host reads or workload execution on import."""
import math

GATE_WAIT_SECONDS = 300
CONTROLLER_ALLOWANCE_SECONDS = 60  # Existing outer deadline: job + 360.
PROCESS_CLEANUP_SECONDS = 45
PREPARATION_SECONDS = 30  # Existing reclaim helper is bounded at 20 seconds.
SETUP_SECONDS = 120
ROLLBACK_SECONDS = 120
MAX_JOB_SECONDS = 7200


def positive_seconds(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError('duration must be a positive finite number')
    if not math.isfinite(value) or value <= 0:
        raise ValueError('duration must be a positive finite number')
    return value


def full_suite_seconds(api31_complete, api36_partial, screenshots_complete):
    # The partial run is a lower bound, not a full-suite duration or case count.
    # Include an entire separate screenshot pass, then add 50% runtime margin.
    observed = max(positive_seconds(api31_complete), positive_seconds(api36_partial))
    projected = (observed + positive_seconds(screenshots_complete)) * 1.5
    seconds = math.ceil(projected / 900) * 900
    if seconds > MAX_JOB_SECONDS:
        raise ValueError('observations exceed the finite job policy; replan explicitly')
    return seconds


def job_bound(seconds):
    if type(seconds) is not int or not 0 < seconds <= MAX_JOB_SECONDS:
        raise ValueError('job timeout must be an integer within the finite policy')
    return seconds + GATE_WAIT_SECONDS + CONTROLLER_ALLOWANCE_SECONDS + PROCESS_CLEANUP_SECONDS + PREPARATION_SECONDS


def window_bounds(plan):
    if not isinstance(plan, (list, tuple)) or not plan:
        raise ValueError('a nonempty finite plan is required')
    jobs = [job_bound(job[3]) for job in plan]
    total = SETUP_SECONDS + sum(jobs) + ROLLBACK_SECONDS
    return {'job_bounds_seconds': jobs,
            'fallback_seconds': math.ceil(total / 60) * 60,
            'setup_seconds': SETUP_SECONDS, 'rollback_seconds': ROLLBACK_SECONDS}
