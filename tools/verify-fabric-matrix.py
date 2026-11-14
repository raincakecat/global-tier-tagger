"""Run the requested three Fabric API versions per MC target. No implicit retries."""
from pathlib import Path
import subprocess,json,time,hashlib,shutil,argparse
ROOT=Path(__file__).resolve().parents[1]
projects={'1.21.11':'mc12111','26.1.2':'mc2612','26.2':'mc262','26.3':'mc263'}
parser=argparse.ArgumentParser();parser.add_argument('--minecraft',choices=list(projects));parser.add_argument('--manifest',default='build/verification/fabric-api-compatibility-versions.json');parser.add_argument('--output',default='build/verification/fabric-api-matrix-results.json');parser.add_argument('--folder',default='build/verification/fabric-matrix');args=parser.parse_args()
manifest=json.loads((ROOT/args.manifest).read_text())
result_path=ROOT/args.output
results=[r for r in json.loads(result_path.read_text())['results'] if r['minecraft']!=args.minecraft] if args.minecraft and result_path.exists() else []
for mc,versions in manifest['versions'].items():
 if args.minecraft and mc!=args.minecraft:continue
 for api in reversed(versions):
  project=projects[mc];folder=ROOT/args.folder/project/api.replace('+','_');folder.mkdir(parents=True,exist_ok=True)
  print(f'START {mc} Fabric API {api}',flush=True);started=time.time()
  with (folder/'gradle.log').open('w',encoding='utf-8') as log:
   run=subprocess.run(['cmd.exe','/d','/c',str(ROOT/'gradlew.bat'),f'-PfabricApi.{project}={api}',f':{project}:runClientGameTest'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  bytecode=hashlib.sha256()
  for path in sorted((ROOT/project/'build/classes/java/main').rglob('*.class')):bytecode.update(path.relative_to(ROOT/project/'build/classes/java/main').as_posix().encode());bytecode.update(path.read_bytes())
  item={'minecraft':mc,'fabricApi':api,'exitCode':run.returncode,'seconds':round(time.time()-started,1),'productionBytecodeSha256':bytecode.hexdigest(),'log':str(folder/'gradle.log')};results.append(item)
  for sub in ['screenshots','logs']:
   source=ROOT/project/'build/run/clientGameTest'/sub
   if source.exists():shutil.copytree(source,folder/sub,dirs_exist_ok=True)
  (ROOT/args.output).write_text(json.dumps({'metadata':manifest,'results':results},indent=2))
  print(f"END {mc} {api}: {'PASS' if run.returncode==0 else 'FAIL'} ({item['seconds']}s)",flush=True)
print('MATRIX FINISHED',flush=True)
raise SystemExit(0 if all(row['exitCode']==0 for row in results) else 1)
