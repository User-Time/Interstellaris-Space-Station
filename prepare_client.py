#!/usr/bin/env python3
"""Prepare official, SHA1-verified Minecraft client classes for compilation only."""
from pathlib import Path
import argparse,hashlib,json,subprocess,urllib.request
import build
ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--server-root',type=Path,default=build.DEFAULT_SERVER_ROOT);args=parser.parse_args()
WORK=ROOT/'build/client';WORK.mkdir(parents=True,exist_ok=True)
def fetch_json(url):
    with urllib.request.urlopen(url,timeout=45) as response:return json.load(response)
def download(entry,name):
    target=WORK/name
    if target.exists() and hashlib.sha1(target.read_bytes()).hexdigest()==entry['sha1']:return target
    with urllib.request.urlopen(entry['url'],timeout=90) as response:data=response.read()
    if hashlib.sha1(data).hexdigest()!=entry['sha1']:raise ValueError('Mojang SHA1 mismatch: '+name)
    target.write_bytes(data);return target
manifest=fetch_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
entry=next(x for x in manifest['versions'] if x['id']=='1.21.1')
version=fetch_json(entry['url']);(WORK/'version.json').write_text(json.dumps(version,indent=2))
client=download(version['downloads']['client'],'client-obfuscated.jar')
mapping=download(version['downloads']['client_mappings'],'client-mappings.txt')
output=WORK/'client-mapped.jar'
if not output.exists():
    server=args.server_root.resolve()
    tool=server/'libraries/net/neoforged/AutoRenamingTool/2.0.3/AutoRenamingTool-2.0.3-all.jar'
    args=['--input',str(client),'--output',str(output),'--map',str(mapping),'--reverse','--threads','2','--ann-fix','--record-fix','--ids-fix','--src-fix']
    for jar in sorted((server/'libraries').rglob('*.jar')):args+=['--lib',str(jar)]
    cfg=WORK/'remap.cfg';cfg.write_text('\n'.join(args)+'\n')
    cmd=['docker','run','--rm','--network','none','--user','1000:1000','--cap-drop','ALL','--memory','1024m','--memory-swap','1024m','--cpus','2',
         '--mount',f'type=bind,src={server},dst={server},readonly','--mount',f'type=bind,src={WORK},dst={WORK}',
         '--entrypoint','java','eclipse-temurin:25-jdk-jammy','-Xmx768m','-jar',str(tool),'--cfg',str(cfg)]
    with (WORK/'remap.log').open('w') as log:subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
(WORK/'provenance.json').write_text(json.dumps({'version':entry,'client':version['downloads']['client'],'client_mappings':version['downloads']['client_mappings'],
       'mapped_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'purpose':'compile-time dependency only; never redistribute Minecraft classes in the mod'},indent=2))
print(output)
