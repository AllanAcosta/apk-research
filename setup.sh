#!/usr/bin/env bash
set -euo pipefail
mkdir -p /workspace/tooling /workspace/android-sdk
python3 - <<'PY'
import os,urllib.request,hashlib,zipfile
items=[
 ('https://dl.google.com/android/repository/platform-35_r02.zip','0bb560a90a7a2cbd0dd8348224d518b638fe7949','platforms/android-35'),
 ('https://dl.google.com/android/repository/build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc','build-tools/35.0.0'),
 ('https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip','477254aa5f903c15cf51001717bdf347fb6b53e0','platform-tools')]
for url,digest,target in items:
 dest='/workspace/android-sdk/'+target
 if os.path.isfile(dest+'/source.properties'):continue
 archive='/workspace/tooling/'+url.rsplit('/',1)[1]
 if not os.path.isfile(archive):urllib.request.urlretrieve(url,archive)
 with open(archive,'rb') as f:assert hashlib.file_digest(f,'sha1').hexdigest()==digest,'Checksum mismatch: '+url
 with zipfile.ZipFile(archive) as z:
  for entry in z.infolist():
   relative='/'.join(entry.filename.split('/')[1:])
   if not relative:continue
   dst=os.path.join(dest,relative)
   if entry.is_dir():os.makedirs(dst,exist_ok=True)
   else:
    os.makedirs(os.path.dirname(dst),exist_ok=True)
    with z.open(entry) as src,open(dst,'wb') as out:out.write(src.read())
    mode=entry.external_attr>>16&0o777
    if mode:os.chmod(dst,mode)
url='https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar'
path='/workspace/tooling/ecj.jar'
if not os.path.isfile(path):urllib.request.urlretrieve(url,path)
with urllib.request.urlopen(url+'.sha1') as response:digest=response.read().decode().strip()
with open(path,'rb') as f:assert hashlib.file_digest(f,'sha1').hexdigest()==digest,'ECJ checksum mismatch'
PY
bash /workspace/apk-research/build.sh
