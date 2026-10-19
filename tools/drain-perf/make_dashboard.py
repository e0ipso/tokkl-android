#!/usr/bin/env python3
"""Build a self-contained view of the measured costs, with units kept separate."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
data = json.loads((ROOT / 'generated/analysis.json').read_text())
html = '''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Measured drain costs</title>
<style>
body{font:14px/1.5 var(--font-sans,system-ui);color:var(--foreground,#202631);margin:0}
h2{font-size:21px;margin:0 0 8px;font-weight:650}p{margin:7px 0 14px}
button{font:inherit;padding:5px 12px;border:1px solid var(--border,#cad0d8);border-radius:6px;background:transparent;color:inherit;cursor:pointer}
button[aria-pressed=true]{background:var(--accent-surface,#e4eafa);border-color:var(--accent,#5768a0)}
nav{display:flex;gap:7px;margin-bottom:16px}.measure{display:grid;grid-template-columns:1fr auto;gap:8px;margin:11px 0}
.track{grid-column:1/-1;background:var(--muted,#e7e9ed);height:11px;border-radius:4px;overflow:hidden}
.fill{height:100%;background:var(--chart-1,#637eb5);border-radius:4px}
.detail{color:var(--muted-foreground,#596575);font-size:13px}.stats{display:flex;flex-wrap:wrap;gap:12px 28px;margin:18px 0 12px}.stat strong{display:block;font-size:23px;font-weight:600}
.note{border-top:1px solid var(--border,#d4dae0);padding-top:12px;margin-top:18px}
@media(max-width:420px){body{font-size:13px}.stats{gap:12px 20px}}
</style>
<h2>Measured drain costs</h2>
<p class="detail">Summed child-agent minutes. Parallel work overlaps; these bars do not add up to wave elapsed time.</p>
<nav aria-label="Wave"><button data-wave="7" aria-pressed="true">Wave 7</button><button data-wave="6" aria-pressed="false">Wave 6</button></nav>
<div id="bars"></div><div class="stats" id="stats"></div>
<p class="detail" id="orientation"></p>
<p class="note">Wave 7's repeated close suite used the same Git tree as coverage. Its second API 36 run cost <strong>26m29s inside Gradle</strong>. Wave 6 repeats another <strong>10m42s</strong>.</p>
<p class="detail">Boot, container and teardown times were not individually logged. Daemon/configuration-cache savings and historical session-loss costs remain unverified.</p>
<script>
const DATA=__DATA__;
const labels=[['device','Device commands, including lock waits'],['gradle','Separate Gradle commands'],['wait','Other waits'],['model_or_unobserved','Coding, reasoning and unobserved time'],['read','Explicit read intervals']];
function show(wave){
 const d=DATA[wave],p=d.partition_s,total=d.agent_wall_s;
 document.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.wave===wave)));
 const bars=document.getElementById('bars');bars.replaceChildren();
 for(const [key,label] of labels){const row=document.createElement('div');row.className='measure';
 const name=document.createElement('span');name.textContent=label;
 const value=document.createElement('span');value.textContent=(p[key]/60).toFixed(1)+' min';
 const track=document.createElement('div');track.className='track';const fill=document.createElement('div');fill.className='fill';fill.style.width=(100*p[key]/total)+'%';
 fill.title=(100*p[key]/total).toFixed(1)+'% of summed agent time';track.append(fill);row.append(name,value,track);bars.append(row);}
 const stats=document.getElementById('stats');stats.replaceChildren();
 for(const [value,label] of [[d.observed_device_log_count,'device invocation logs'],[d.boots,'boots'],[(d.lock_wait_s/60).toFixed(1)+' min','recorded lock waits']]){const s=document.createElement('div');s.className='stat';const strong=document.createElement('strong');strong.textContent=value;const caption=document.createElement('span');caption.textContent=label;s.append(strong,caption);stats.append(s);}
 document.getElementById('orientation').textContent='Median time before first explicit edit: '+(d.median_first_edit_s/60).toFixed(1)+' min. This includes baseline checks and may include earlier Bash edits.';
 console.assert(Math.abs(Object.values(p).reduce((a,b)=>a+b,0)-total)<1,'partition must equal total');
}
document.querySelectorAll('button').forEach(b=>b.addEventListener('click',()=>show(b.dataset.wave)));show('7');
</script></html>'''.replace('__DATA__', json.dumps(data))
(ROOT / 'dashboard.html').write_text(html)
print('Wrote dashboard.html')
