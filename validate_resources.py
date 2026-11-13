#!/usr/bin/env python3
"""Validate packaged resources against the pinned, official 1.21.1 client; no graphics acceptance implied."""
from pathlib import Path
import json,zipfile
import build
p=Path(__file__).resolve().parent;checked=[]
with zipfile.ZipFile(p/'build/client/client-mapped.jar') as vanilla:
 names=set(vanilla.namelist())
 for f in (p/'src/main/resources').rglob('*.json'):
  v=json.loads(f.read_text());checked.append(str(f.relative_to(p)))
  if '/models/block/' in str(f):
   for texture in v.get('textures',{}).values():
    namespace,path=texture.split(':',1)
    if namespace=='minecraft':assert f'assets/{namespace}/textures/{path}.png' in names,texture
with zipfile.ZipFile(p/f'dist/{build.ARTIFACT}-{build.VERSION}.jar') as jar:
 assert not [n for n in jar.namelist() if n.startswith(('incident/','net/minecraft/','com/interstellaris/','dev/ryanhcode/'))]
 assert not [n for n in jar.namelist() if n.startswith('org/potgcraft/')]
 for f in (p/'src/main/resources').rglob('*'):
  if f.is_file():assert jar.read(str(f.relative_to(p/'src/main/resources')))==f.read_bytes(),f
result={'valid_json_files':len(checked),'vanilla_textures_exist':True,'packaged_resources_match_source':True,'bundled_game_or_probe_classes':False,'graphical_client_tested':False}
(p/'build/resource-results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
