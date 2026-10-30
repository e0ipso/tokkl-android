import pathlib, hashlib, subprocess, sys, json, shutil, xml.etree.ElementTree as ET, time
root=pathlib.Path.cwd(); dest=root/'.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase3/task5'/sys.argv[1]; dest.mkdir(parents=True,exist_ok=True)
cmd=sys.argv[2:]
def hashes():
    files=[]
    for folder in ['app/src','gradle','scripts','test']:
        files+=list((root/folder).rglob('*'))
    files += [root/f for f in ['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','app/build.gradle.kts','package.json','package-lock.json']]
    return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(files)) if f.is_file()}
before=hashes(); (dest/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
started=time.time()
(dest/'command.json').write_text(json.dumps({'argv':cmd,'outer':f'flock --close /tmp/tokkl-plan3-gradle.lock systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- python3 {pathlib.Path(__file__).resolve()} ...','started':started},indent=2)+'\n')
with (dest/'run.log').open('w') as log:
    result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
(dest/'exit-code.txt').write_text(str(result.returncode)+'\n')
after=hashes(); (dest/'inputs-after.json').write_text(json.dumps(after,indent=2)+'\n')
(dest/'input-changes.json').write_text(json.dumps([f for f in set(before)|set(after) if before.get(f)!=after.get(f)],indent=2)+'\n')
paths=['app/build/outputs/androidTest-results/connected/debug','app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected'] if 'scripts/instrumented-tests.sh' in cmd else (['app/build/test-results/testDebugUnitTest','app/build/reports/lint-results-debug.xml'] if ':app:testDebugUnitTest' in cmd else [])
for src in paths:
    src=root/src
    if src.is_dir():
        for f in src.rglob('*'):
            if f.is_file() and f.stat().st_mtime >= started and f.suffix in ['.xml','.png','.json']:
                to=dest/'results'/src.name/f.relative_to(src);to.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,to)
    elif src.exists() and src.stat().st_mtime >= started: shutil.copy2(src,dest/src.name)
receipts=[]
for src in paths:
    for f in (root/src).rglob('logcat-*.txt'):
        if f.stat().st_mtime < started: continue
        lines=[line for line in f.read_text(errors='replace').splitlines() if 'TestRunner' in line and ('started:' in line or 'finished:' in line)]
        receipts.append({'file':f.name,'test_runner_lines':lines})
(dest/'process-isolation.json').write_text(json.dumps(receipts,indent=2)+'\n')
cases=[]
for xml in (dest/'results').rglob('TEST-*.xml'):
    tree=ET.parse(xml)
    for c in tree.findall('.//testcase'):
        cases.append({'class':c.get('classname'),'name':c.get('name'),'time':c.get('time'),'failure':[(f.tag,f.get('message','')) for f in c if f.tag in ['failure','error','skipped']]})
summary={'exit':result.returncode,'cases':len(cases),'failures':sum(any(f[0] in ['failure','error'] for f in c['failure']) for c in cases),'skipped':sum(any(f[0]=='skipped' for f in c['failure']) for c in cases),'inputs_unchanged':before==after}
(dest/'cases.json').write_text(json.dumps(cases,indent=2)+'\n');(dest/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary))
sys.exit(result.returncode)
