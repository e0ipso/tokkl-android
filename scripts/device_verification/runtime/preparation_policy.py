"""Finite reservation binding. No reclaim, process or resource actions on import."""
import argparse
import json
import os
from pathlib import Path

from configuration import adopted_configuration, canonical, require


def helper_request(directory, argv):
    """Validate before the helper even opens memory.reclaim or takes a lock."""
    directory = Path(directory).resolve()
    write = bool(argv and argv[0] == '--write')
    parser = argparse.ArgumentParser()
    parser.add_argument('amount', type=int)
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--preparation', type=int, required=True)
    args = parser.parse_args(argv[1:] if write else argv)
    adoption = os.environ.get('TOKKL_VERIFICATION_ADOPTION')
    require(adoption and Path(adoption).is_absolute(), 'helper requires matching root adoption')
    config = adopted_configuration(directory, adoption)
    state_path = args.state
    require(state_path.is_absolute() and state_path.resolve() == state_path
            and state_path.parent == directory and state_path.name.startswith('window-state-')
            and state_path.suffix == '.json', 'helper state path is not bound to this run')
    state = json.loads(state_path.read_text())
    require(state.get('configuration_sha256') == config['_sha256']
            and state.get('run_id') == config['run_id'] and state.get('lease_id') == config['lease_id']
            and state.get('project') == config['project'], 'reservation identity/project/configuration mismatch')
    require(state.get('phase') in ['prepared', 'applying', 'applied', 'testing'], 'reservation phase is inactive')
    rows = state['preparations']
    limit = config['preparation_policy']['max_preparations']
    require(type(args.preparation) is int and args.preparation == len(rows)-1
            and 0 <= args.preparation < len(config['plan']) and len(rows) <= limit,
            'helper must use the newest finite preparation')
    row = rows[args.preparation]
    require(row['label'] == config['plan'][args.preparation]['label']
            and len({r['label'] for r in rows}) == len(rows), 'preparation job identity/replay mismatch')
    cap = config['preparation_policy']['max_request_bytes']
    require(0 < args.amount <= cap and row.get('requested') == args.amount and row.get('write_count') == 1,
            'request exceeds explicit cap or differs from reserved write')
    requested = [r.get('requested', 0) for r in rows]
    require(all(type(x) is int and 0 <= x <= cap for x in requested), 'invalid preparation reservation')
    require(state['reclaim_reserved_bytes'] == config['carried_reclaim_bytes']+sum(requested)
            and state['reclaim_budget_bytes'] == config['reclaim_budget_bytes']
            and state['reclaim_reserved_bytes'] <= config['reclaim_budget_bytes'],
            'cumulative reservations changed, exceeded budget or were refunded')
    require(args.amount == row['deficit']+134217728
            and args.amount <= row['before']['stat']['inactive_file'],
            'write must be fresh full deficit plus 128 MiB and covered by inactive file cache')
    expected = ['/usr/bin/python3', str(directory/'local-reclaim-finite.py'), str(args.amount),
                '--state', str(state_path), '--preparation', str(args.preparation)]
    require(row.get('command') == expected, 'reserved helper argv/path mismatch')
    return {'amount': args.amount, 'state_path': state_path, 'preparation': args.preparation,
            'configuration_sha256': config['_sha256'], 'write': write,
            'intent_path': directory/('preparation-write-'+str(args.preparation)+'.json')}


def reserve_write(request):
    """One durable write intent per preparation, with no retry/refund on error."""
    path = request['intent_path']
    with path.open('x') as out:
        os.chmod(path, 0o600)
        out.write(canonical({k: str(v) if isinstance(v, Path) else v
                             for k, v in request.items()}).decode()+'\n')
        out.flush()
        os.fsync(out.fileno())
    fd = os.open(path.parent, os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)
