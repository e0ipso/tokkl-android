from pathlib import Path
import hashlib
import sys

paths = set(Path('app/src/main/java/com/github/e0ipso/tokkl/widget').glob('*.kt'))
paths.update(Path('app/src/androidTest/java/com/github/e0ipso/tokkl/widget').glob('*.kt'))
paths.update(Path('app/src/test/java/com/github/e0ipso/tokkl/widget').glob('*.kt'))
paths.update(Path('app/src/main/res').glob('*/pomodoro_widget*'))
paths.update(Path('app/src/androidTest/assets/screenshots').glob('pomodoro_widget_*.png'))
paths.update(Path(p) for p in [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/github/e0ipso/tokkl/TokklApplication.kt',
    'app/src/main/res/drawable/ic_widget_next.xml',
    'app/src/main/res/layout/widget_chronometer.xml',
    'app/build.gradle.kts', 'gradle/libs.versions.toml', 'gradle.properties',
    'gradle/wrapper/gradle-wrapper.properties', 'scripts/instrumented-tests.sh',
])
Path(sys.argv[1]).write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p)+'\n' for p in sorted(paths) if p.is_file()))
