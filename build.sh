#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/workspace/android-sdk}"
TOOLS="$SDK/build-tools/35.0.0"
ECJ="${ECJ_JAR:-/workspace/tooling/ecj.jar}"
OUT="$PWD/build"
KEY="${SIGNING_KEY:-/workspace/tooling/keys/visor-local.jks}"
mkdir -p "$OUT/classes" "$OUT/dex" "$(dirname "$KEY")"
find "$OUT/classes" "$OUT/dex" -type f -delete
java -jar "$ECJ" -8 -proc:none -bootclasspath "$SDK/platforms/android-35/android.jar:$TOOLS/core-lambda-stubs.jar" -d "$OUT/classes" app/src/local/lens/*.java
"$TOOLS/aapt2" compile --dir app/res -o "$OUT/resources.zip"
"$TOOLS/aapt2" link "$OUT/resources.zip" -o "$OUT/base.apk" --manifest AndroidManifest.xml -I "$SDK/platforms/android-35/android.jar" -A app/assets
python3 - "$OUT" <<'PY'
import sys,zipfile,pathlib
out=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for f in (out/'classes').rglob('*.class'):z.write(f,f.relative_to(out/'classes'))
PY
"$TOOLS/d8" --min-api 26 --lib "$SDK/platforms/android-35/android.jar" --output "$OUT/dex" "$OUT/classes.jar"
python3 - "$OUT" <<'PY'
import zipfile,pathlib,sys,shutil
out=pathlib.Path(sys.argv[1]);shutil.copyfile(out/'base.apk',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
 for f in (out/'dex').glob('*.dex'):z.write(f,f.name)
PY
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KEY" ]; then
 keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias visor-local -dname 'CN=Visor Local Development' -keyalg RSA -keysize 3072 -validity 10000 -noprompt
 chmod 600 "$KEY"
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias visor-local --ks-pass pass:android --key-pass pass:android --out "$OUT/visor-local-1.1.apk" "$OUT/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUT/visor-local-1.1.apk"
sha256sum "$OUT/visor-local-1.1.apk"
