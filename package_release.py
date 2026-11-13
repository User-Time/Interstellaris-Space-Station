#!/usr/bin/env python3
"""Package only mod source and public documentation, never server data or dependencies."""
from pathlib import Path
import hashlib,json,zipfile
import build
p=Path(__file__).resolve().parent;stem=f"{build.ARTIFACT}-{build.VERSION}";jar=p/f"dist/{stem}.jar"
source=p/f"dist/{stem}-source.zip"
include=['src','docs','README.md','LICENSE','.gitignore','.gitattributes','build.py','build_tests.py','prepare_client.py','test_navigation_server.py','validate_navigation_api.py','validate_resources.py','package_release.py']
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
 for name in include:
  f=p/name
  for item in sorted(f.rglob('*')) if f.is_dir() else [f]:
   if item.is_file():z.write(item,str(item.relative_to(p)))
artifact=p/f'dist/{stem}-candidate.zip'
public={'name':'Interstellaris: Space Stations','version':build.VERSION,'java_package':'net.time_cloud','mod_id':'potg_station_habitat','network_protocol':'4','status':'preview; client acceptance pending','jar':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
manifest=p/f'dist/RELEASE-{build.VERSION}.json';manifest.write_text(json.dumps(public,indent=2)+'\n')
with zipfile.ZipFile(artifact,'w',zipfile.ZIP_DEFLATED) as z:
 for f in [jar,source,manifest,p/'README.md',p/'docs/TESTING.md',p/'docs/ROLLBACK.md']:z.write(f,f.name)
checks={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in [jar,source,artifact]}
(p/f'dist/SHA256SUMS-{build.VERSION}.txt').write_text(''.join(f'{h}  {name}\n' for name,h in checks.items()))
for f in [source,artifact]:
 with zipfile.ZipFile(f) as z:assert z.testzip() is None
print(json.dumps(checks,indent=2))
