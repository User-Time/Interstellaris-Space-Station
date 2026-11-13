#!/usr/bin/env python3
"""Compile model tests and the isolated server probe; never starts or modifies a server."""
from pathlib import Path
import argparse,subprocess
import build

p=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--server-root',type=Path,default=build.DEFAULT_SERVER_ROOT);args=parser.parse_args()
root=args.server_root.resolve()
if not (p/'build/classes/net/time_cloud/HabitatRuntime.class').exists():
    raise SystemExit('Run python3 build.py first')
build.compile_sources(root,sorted((p/'src/test/java/net/time_cloud').glob('*.java')),p/'build/model-classes',[p/'build/classes'])
build.compile_sources(root,sorted((p/'src/test/java/incident').glob('*.java')),p/'build/probe-classes',[p/'build/classes'])
build.package(p/'build/probe-classes',p/'src/test/resources',p/'build/HabitatProbe.jar')
command=['docker','run','--rm','--network','none','--user','1000:1000','--cap-drop','ALL','--memory','384m','--memory-swap','384m',
         '--mount',f'type=bind,src={root},dst={root},readonly','--mount',f'type=bind,src={p},dst={p},readonly','--entrypoint','java','eclipse-temurin:25-jdk-jammy',
         '-cp',f'{p}/build/classes:{p}/build/model-classes:{root}/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar',
         'net.time_cloud.ModelTests']
result=subprocess.run(command,capture_output=True,text=True)
(p/'build/model-results.txt').write_text(result.stdout+result.stderr)
print(result.stdout+result.stderr,end='')
if result.returncode:raise SystemExit(result.returncode)
command[-1]='net.time_cloud.DomeTests'
result=subprocess.run(command,capture_output=True,text=True)
(p/'build/dome-results.txt').write_text(result.stdout+result.stderr)
print(result.stdout+result.stderr,end='')
if result.returncode:raise SystemExit(result.returncode)
command[-1]='net.time_cloud.NavigationTests'
result=subprocess.run(command,capture_output=True,text=True)
(p/'build/navigation-model-results.txt').write_text(result.stdout+result.stderr)
print(result.stdout+result.stderr,end='')
if result.returncode:raise SystemExit(result.returncode)
print('Compiled isolated probe: build/HabitatProbe.jar. NEVER install it on production: it stops its server after testing.')
