#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/host-tests
java -jar /workspace/tooling/ecj.jar -17 -proc:none -d build/host-tests app/src/local/lens/LocalServer.java tests/local/lens/*.java
java -cp build/host-tests local.lens.ServerTest
python3 - <<'PY'
from pathlib import Path
html=Path('app/assets/dashboard.html').read_text()
Path('build/dashboard.js').write_text(html.split('<script>',1)[1].split('</script>',1)[0])
PY
node --check build/dashboard.js
