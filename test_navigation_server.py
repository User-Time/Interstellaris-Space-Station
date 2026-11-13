#!/usr/bin/env python3
"""Run the optional navigation probe in a blank isolated Youer world. Never installs anything on the source server."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess
import build
p=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('phase',choices=['1','2'])
parser.add_argument('--server-root',type=Path,default=build.DEFAULT_SERVER_ROOT)
parser.add_argument('--test-dir',type=Path,required=True)
parser.add_argument('--container-prefix',default='iss-navigation-test')
parser.add_argument('--server-jar',default='youer-1.21.1-2dd45af8-server.jar')
a=parser.parse_args();root=a.server_root.resolve();test=a.test_dir.resolve()
if test==root or test.is_relative_to(root/'world') or test.is_relative_to(root/'SuperiorWorld'):raise SystemExit('Test directory must not be a production world')
if a.phase=='1':
 if test.exists():raise SystemExit('Test directory exists; preserve the evidence and choose a new directory')
 test.mkdir(parents=True,mode=0o700);(test/'mods').mkdir();(test/'config').mkdir()
 for name in ['eula.txt',a.server_jar]:shutil.copy2(root/name,test/name)
 properties={'server-ip':'127.0.0.1','server-port':'25565','online-mode':'false','white-list':'true','enable-rcon':'false','enable-query':'false','level-name':'probe-world','level-type':'minecraft:flat','generator-settings':'{"biome":"minecraft:the_void","layers":[],"features":false}', 'generate-structures':'false','view-distance':'2','simulation-distance':'2','spawn-protection':'0','spawn-monsters':'false','spawn-animals':'false','spawn-npcs':'false','sync-chunk-writes':'true','max-tick-time':'60000','motd':'Isolated navigation test'}
 (test/'server.properties').write_text(''.join(f'{k}={v}\n' for k,v in properties.items()))
 copied={}
 for f in (root/'mods').glob('*.jar'):
  if f.name.startswith(('potgcraft-station-habitat-','interstellaris-space-stations-')):continue
  shutil.copy2(f,test/'mods'/f.name);copied[f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
 (test/'copied-mods-sha256.json').write_text(json.dumps(copied,indent=2))
else:
 if not (test/'expected-navigation-id.txt').exists():raise SystemExit('First phase has not completed successfully')
 for f in ['logs/latest.log','navigation-results.txt']:
  if (test/f).exists():shutil.copy2(test/f,test/(Path(f).name+'.before-restart'))
# Only this independent test directory is mutated; preserve old candidate JARs outside its mods directory.
for f in (test/'mods').glob('*.jar'):
 if f.name.startswith(('potgcraft-station-habitat-','interstellaris-space-stations-')):
  saved=test/'previous-candidates';saved.mkdir(exist_ok=True);shutil.move(str(f),str(saved/f.name))
shutil.copy2(p/f'dist/{build.ARTIFACT}-{build.VERSION}.jar',test/'mods'/f'{build.ARTIFACT}-{build.VERSION}.jar')
shutil.copy2(p/'build/HabitatProbe.jar',test/'mods/HabitatProbe.jar')
name=a.container_prefix+'-'+a.phase
cmd=['docker','run','-d','--name',name,'--network','none','--user','1000:1000','--cap-drop','ALL','--security-opt','no-new-privileges','--memory','2200m','--memory-swap','2200m','--cpus','2',
 '--mount',f'type=bind,src={test},dst=/minecraft',
 '--mount',f'type=bind,src={root}/libraries,dst=/minecraft/libraries,readonly',
 '-w','/minecraft','--entrypoint','java','eclipse-temurin:25-jdk-jammy','-Xms512M','-Xmx1408M','-Dfile.encoding=UTF-8','-Dhabitat.probe.navigation=true',f'-Dhabitat.probe.phase={a.phase}',
 '-jar',a.server_jar,'nogui']
result=subprocess.run(cmd,capture_output=True,text=True);print(result.stdout+result.stderr,end='');raise SystemExit(result.returncode)
