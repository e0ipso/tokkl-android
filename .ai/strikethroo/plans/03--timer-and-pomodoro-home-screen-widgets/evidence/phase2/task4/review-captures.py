"""Arrange this task's fresh API36 captures for review, without altering references."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent
run = root / sys.argv[1]
images = {p.name: p for p in run.rglob('pomodoro_widget_*.png') if p.parent.name == 'actual'}
out = root / (sys.argv[1] + '-review')
out.mkdir(exist_ok=True)
variants = ['light_normal', 'dark_normal', 'light_largest', 'dark_largest']
groups = [['work', 'short_rest', 'long_rest'], ['work_done', 'short_rest_done', 'long_rest_done'], ['idle', 'signed_out', 'unsupported']]
for variant in variants:
    for group_index, states in enumerate(groups):
        args = []
        for state in states:
            for size in ['196x100', '298x222', '375x338']:
                name = f'pomodoro_widget_{variant}_{size}_{state}.png'
                args.extend(['-label', f'{size} {state}', str(images[name])])
        target = out / f'{variant}-{group_index}.png'
        subprocess.run(['montage', '-background', '#888888', '-fill', 'black', '-font', 'DejaVu-Sans', '-pointsize', '12', *args, '-tile', '3x3', '-geometry', '+4+20', str(target)], check=True)
args = []
for states in groups:
    for state in states:
        for variant in variants:
            name = f'pomodoro_widget_{variant}_110x40_{state}.png'
            args.extend(['-label', f'{variant} {state}', str(images[name])])
subprocess.run(['montage', '-background', '#888888', '-fill', 'black', '-font', 'DejaVu-Sans', '-pointsize', '9', *args, '-tile', '4x9', '-geometry', '+3+15', str(out / 'minimum-sizes.png')], check=True)
print(out)
