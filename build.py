#!/usr/bin/env python3
"""Compile against the installed, version-pinned server; never installs the resulting JAR."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, zipfile

PROJECT = Path(__file__).resolve().parent
VERSION = '2.1.1'
ARTIFACT = 'interstellaris-space-stations'
DEFAULT_SERVER_ROOT = Path(os.environ.get('MC_SERVER_ROOT', PROJECT.parents[1]))

def classpath(root, build):
    first = [root/'libraries/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-server.jar',
             root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar',
             root/'libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar']
    deps = build/'deps'; deps.mkdir(parents=True, exist_ok=True)
    for jar in [root/'libraries/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-universal.jar',
                root/'mods/sable-neoforge-1.21.1-2.0.5.jar']:
        with zipfile.ZipFile(jar) as source:
            for name in source.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    (deps/Path(name).name).write_bytes(source.read(name))
    rest = sorted((root/'libraries').rglob('*.jar')) + sorted((root/'mods').glob('*.jar')) + sorted(deps.glob('*.jar'))
    client=build/'client/client-mapped.jar'
    if not client.is_file():raise FileNotFoundError('Run prepare_client.py to prepare official client compilation classes')
    rest.insert(0,client)
    paths = list(dict.fromkeys(first+rest))
    for path in paths:
        if not path.is_file(): raise FileNotFoundError(path)
    return ':'.join(map(str,paths))

def compile_sources(root, sources, destination, extra=()):
    build=PROJECT/'build';build.mkdir(exist_ok=True)
    destination=destination.resolve()
    if not destination.is_relative_to(build.resolve()):raise ValueError('Compilation output must be inside build/')
    if destination.exists():shutil.rmtree(destination)
    destination.mkdir(parents=True,exist_ok=True)
    cp=classpath(root,build)
    if extra: cp=':'.join(map(str,extra))+':'+cp
    args=['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',str(destination)]+[str(s) for s in sources]
    argfile=build/'compile.args';argfile.write_text('\n'.join(json.dumps(s,ensure_ascii=False) for s in args))
    command=['docker','run','--rm','--network','none','--user','1000:1000','--cap-drop','ALL','--security-opt','no-new-privileges',
             '--memory','768m','--memory-swap','768m','--cpus','2','--mount',f'type=bind,src={root},dst={root},readonly',
             '--mount',f'type=bind,src={PROJECT},dst={PROJECT}','--entrypoint','javac','eclipse-temurin:25-jdk-jammy','@'+str(argfile)]
    result=subprocess.run(command,capture_output=True,text=True)
    (build/'last-compile.log').write_text(result.stdout+result.stderr)
    (build/('compile-'+destination.name+'.log')).write_text(result.stdout+result.stderr)
    print(result.stdout+result.stderr,end='')
    if result.returncode: raise SystemExit(result.returncode)
    return cp

def package(classes, resources, output):
    output.parent.mkdir(exist_ok=True,parents=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as jar:
        for base in (classes,resources):
            for path in sorted(base.rglob('*')):
                if path.is_file():
                    entry=zipfile.ZipInfo(str(path.relative_to(base)),(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
                    jar.writestr(entry,path.read_bytes())

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--server-root',type=Path,default=DEFAULT_SERVER_ROOT);args=parser.parse_args()
    root=args.server_root.resolve();classes=PROJECT/'build/classes'
    cp=compile_sources(root,sorted((PROJECT/'src/main/java').rglob('*.java')),classes)
    output=PROJECT/f'dist/{ARTIFACT}-{VERSION}.jar';package(classes,PROJECT/'src/main/resources',output)
    manifest={'jar':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'production_installed':False,
              'source_sha256':{str(p.relative_to(PROJECT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((PROJECT/'src/main').rglob('*')) if p.is_file()}}
    (PROJECT/'build/classpath.txt').write_text(cp);(PROJECT/'dist/BUILD_MANIFEST.json').write_text(json.dumps(manifest,indent=2))
    (PROJECT/f'dist/BUILD_MANIFEST-{VERSION}.json').write_text(json.dumps(manifest,indent=2))
    print(json.dumps({'built':str(output),'sha256':manifest['sha256'],'production_installed':False},indent=2))
