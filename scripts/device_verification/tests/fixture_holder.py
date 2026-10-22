"""Owned subprocess fixture: fake cgroup paths, actual subprocess lifecycle."""
from pathlib import Path
import json,os,shutil,sys
import scope_completion as sc
session,unit,code=sys.argv[1:4];request=sc.load(Path(session)/'request.json');scope=Path(request['app'])/unit
(scope/'member.pid').write_text(str(os.getpid()))
def identity(pid):
    result=sc.process_identity(pid)
    if result is not None:result['cgroup']=str(scope)
    return result
def members(path):
    result={os.getpid()}
    if (scope/'extra-member').exists():result.add(999999999)
    return result
rc=sc.holder_main(['--session',session,'--unit',unit,'--',sys.executable,'-c',code],identity_reader=identity,members_reader=members)
if (scope/'drop-release').exists():
    (Path(session)/'scopes'/unit/'released.json').unlink(missing_ok=True)
shutil.rmtree(scope,ignore_errors=True)
marker=Path(session)/('fixture-closed-'+unit);marker.write_text(str(rc))
raise SystemExit(rc)
