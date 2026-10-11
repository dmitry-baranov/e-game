"""Deploy a saved experimental model release to the ordinary game's ML service.

  python3 ml/promote_model.py
  python3 ml/promote_model.py --run-id <UUID>

Uses only the Python standard library on the host. Joblib validation and predictions
run inside the destination ML container with its installed dependencies.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def docker(*args, code=None):
    command = ['docker', *args]
    result = subprocess.run(command, input=code, text=True, capture_output=True, check=True, cwd=ROOT)
    return result.stdout.strip()


SOURCE_METADATA = '''
import json,sys,joblib,hashlib
from pathlib import Path
from learning import ARTIFACT
requested=sys.argv[1]
path=ARTIFACT if not requested else ARTIFACT.parent/'training-runs'/(requested+'.joblib')
model=joblib.load(path)
run_id=model['training_run_id']
report=ARTIFACT.parent/'training-runs'/(run_id+'.json')
data=json.loads(report.read_text())
digest=hashlib.sha256(path.read_bytes()).hexdigest()
assert data['artifact_sha256']==digest,'Source report does not match model'
print(json.dumps({'run_id':run_id,'artifact':str(path),'report':str(report),
                  'sha256':digest,'name':model['name'],'version':model['version']}))
'''

ACTIVATE = '''
import hashlib,json,joblib,os,shutil,sys,uuid
from datetime import datetime,timezone
from pathlib import Path
from learning import ARTIFACT,FEATURE_VERSION
run_id,expected=sys.argv[1:]
assert os.environ.get('STRATEGY_AUTO_TRAIN')!='true','Disable auto-training before deployment'
stage=ARTIFACT.parent/'.staging'/run_id
weights=stage/'model.joblib'
report_path=stage/'report.json'
assert hashlib.sha256(weights.read_bytes()).hexdigest()==expected,'Transfer checksum mismatch'
report=json.loads(report_path.read_text())
assert report['artifact_sha256']==expected,'Report checksum mismatch'
model=joblib.load(weights)
assert model['training_run_id']==run_id and report['training_run_id']==run_id,'Release IDs differ'
assert model['feature_version']==FEATURE_VERSION,'Incompatible feature version'
assert report['status']=='trained','Release did not train a new model'
assert model['name']==report['champion'],'Model name differs from report'
assert model['version']==report['version'],'Model version differs from report'
for item in [model,*model.get('challengers',[])]:
    assert hasattr(item['model'],'predict'),'Model is not usable'
history=ARTIFACT.parent/'training-runs'
history.mkdir(exist_ok=True)
for source,suffix in [(weights,'.joblib'),(report_path,'.json')]:
    dest=history/(run_id+suffix)
    if dest.exists():
        assert hashlib.sha256(dest.read_bytes()).digest()==hashlib.sha256(source.read_bytes()).digest(), 'Historical release differs'
    else:
        os.link(source,dest)
# Preserve the previous active files before replacing either one.
backup=ARTIFACT.parent/'deployment-backups'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
if ARTIFACT.exists() or ARTIFACT.with_name('last-report.json').exists():
    backup.mkdir(parents=True)
    for previous in (ARTIFACT,ARTIFACT.with_name('last-report.json')):
        if previous.exists(): os.link(previous,backup/previous.name)
os.replace(weights,ARTIFACT)
os.replace(report_path,ARTIFACT.with_name('last-report.json'))
manifest={'training_run_id':run_id,'model':model['name'],'version':model['version'],
          'artifact_sha256':expected,'dataset':report['dataset'],
          'comparisons':[c['name'] for c in model.get('challengers',[])],
          'deployed_at':datetime.now(timezone.utc).isoformat()}
temporary=ARTIFACT.parent/('.deployment-'+str(uuid.uuid4())+'.json')
temporary.write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
os.replace(temporary,ARTIFACT.parent/'deployment.json')
stage.rmdir()
print(json.dumps(manifest))
'''

CHECK_HEALTH = '''
import json,urllib.request
print(urllib.request.urlopen('http://localhost:8090/health',timeout=30).read().decode())
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', help='Saved training UUID; defaults to the active experimental model')
    parser.add_argument('--source', default='e-game-exp-experiment-ml-1')
    parser.add_argument('--target', default='e-game-ml-1')
    args = parser.parse_args()
    if args.source == args.target:
        parser.error('Source and destination must be different containers')
    if args.run_id:
        args.run_id = str(uuid.UUID(args.run_id))
    metadata = json.loads(docker('exec', '-i', args.source, 'python', '-', args.run_id or '', code=SOURCE_METADATA))
    run_id = str(uuid.UUID(metadata['run_id']))
    release = ROOT / 'ml' / 'artifacts' / 'releases' / run_id
    release.mkdir(parents=True, exist_ok=True)
    weights, report = release/'model.joblib', release/'report.json'
    docker('cp', args.source+':'+metadata['artifact'], str(weights))
    docker('cp', args.source+':'+metadata['report'], str(report))
    digest = hashlib.sha256(weights.read_bytes()).hexdigest()
    if digest != metadata['sha256']:
        raise RuntimeError('Export checksum differs from source model')
    stage = '/app/artifacts/.staging/'+run_id
    docker('exec', args.target, 'mkdir', '-p', stage)
    docker('cp', str(weights), args.target+':'+stage+'/model.joblib')
    docker('cp', str(report), args.target+':'+stage+'/report.json')
    deployed = json.loads(docker('exec', '-i', args.target, 'python', '-', run_id, digest, code=ACTIVATE))
    # First health request warms the inference cache before user requests arrive.
    health = json.loads(docker('exec', '-i', args.target, 'python', '-', code=CHECK_HEALTH))
    if not health['ready'] or health['version'] != deployed['version']:
        raise RuntimeError('Destination ML service did not load the deployed version')
    manifest = deployed | {'source_container':args.source,'target_container':args.target,'health':health}
    (release/'deployment.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(manifest,ensure_ascii=False,indent=2))
    print('Local release:', release.relative_to(ROOT))
    print('User application: http://localhost:8080 — game → strategies → request recommendations')


if __name__ == '__main__':
    main()
