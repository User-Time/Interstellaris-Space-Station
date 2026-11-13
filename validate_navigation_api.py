#!/usr/bin/env python3
"""Check pinned binary UI contracts without claiming a graphical client test."""
from pathlib import Path
import argparse,struct,io,json,hashlib,zipfile
import build
p=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--server-root',type=Path,default=build.DEFAULT_SERVER_ROOT);args=parser.parse_args()
jar=args.server_root.resolve()/'mods/Interstellaris-0.6.3a-beta-neoforge-1.21.1.jar'
pin='63349534cb93dfae669d05e3904d4310e7a68b0ce69eba753f71ae4db9811233'
assert hashlib.sha256(jar.read_bytes()).hexdigest()==pin,'Interstellaris binary changed; re-inspect before packaging'
def members(data):
 f=io.BytesIO(data)
 def u(n):return int.from_bytes(f.read(n),'big')
 assert u(4)==0xcafebabe;u(2);u(2);cp={};count=u(2);i=1
 while i<count:
  tag=u(1)
  if tag==1:cp[i]=f.read(u(2)).decode('utf8','replace')
  elif tag in (3,4,9,10,11,12,17,18):f.read(4)
  elif tag in (5,6):f.read(8);i+=1
  elif tag in (7,8,16,19,20):f.read(2)
  elif tag==15:f.read(3)
  else:raise AssertionError(tag)
  i+=1
 f.read(6);f.read(2*u(2))
 def table():
  result={}
  for _ in range(u(2)):
   access,name,desc=u(2),cp[u(2)],cp[u(2)];result[(name,desc)]=access
   for _ in range(u(2)):u(2);f.read(u(4))
  return result
 return table(),table()
contracts={
 'com/interstellaris/mod/client/ShipInformationCenterScreen':[
 ('entries','()Ljava/util/List;'),('system','()Lnet/minecraft/resources/ResourceKey;'),('shipPosition','(Lnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector3d;')],
 'com/interstellaris/mod/client/ShipInformationCenterScreen$Entry':[
 ('<init>','(Lnet/minecraft/core/Holder$Reference;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;ZD)V')],
 'com/interstellaris/mod/space/CelestialBody':[
 ('name','(Lnet/minecraft/core/Holder;)Lnet/minecraft/network/chat/Component;')],
 'com/interstellaris/mod/ship/Navigator':[
 ('body','(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/core/Holder$Reference;'),
 ('plan','(Lnet/minecraft/server/level/ServerLevel;Lorg/joml/Vector3dc;Lnet/minecraft/core/Holder;Lcom/interstellaris/mod/ship/NavMode;Lcom/interstellaris/mod/ship/Navigator$Stock;Ljava/util/UUID;)Lcom/interstellaris/mod/ship/Navigator$Plan;'),
 ('tick','(Lcom/interstellaris/mod/block/entity/CockpitBlockEntity;Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lcom/interstellaris/mod/block/entity/ShipInformationCenterBlockEntity;Lcom/interstellaris/mod/ship/Navigator$Stock;Lcom/interstellaris/mod/ship/Navigator$View;)V'),
 ('finish','(Lcom/interstellaris/mod/ship/Navigator$View;Ljava/lang/String;)V')],
 'com/interstellaris/mod/network/SicPacket':[('handle','(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V')]
}
checks=[]
with zipfile.ZipFile(jar) as z:
 for cls,expected in contracts.items():
  fields,methods=members(z.read(cls+'.class'))
  for key in expected:assert key in methods,(cls,key);checks.append(cls+'.'+key[0]+key[1])
result={'interstellaris_sha256':pin,'method_contract_checks':len(checks),'checks':checks,'graphical_client_tested':False,'client_mixin_runtime_tested':False}
(p/'build/navigation-api-results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
