"""Read-only smoke check of the deployed user model on a real experimental action."""
import json
import urllib.request
from promote_model import docker

SAMPLE = '''
import json,os,sys,psycopg2
bounds=json.loads(sys.argv[1])
with psycopg2.connect(os.environ['STRATEGY_DATABASE_URL']) as connection:
  with connection.cursor() as cursor:
    cursor.execute("""SELECT e.snapshot_json,e.action_json FROM strategy_training_episode e
      JOIN manufacturer m ON m.id=e.manufacturer_id
      WHERE EXISTS(SELECT 1 FROM experiment_run r JOIN experiment_series s ON s.id=r.series_id
                   WHERE r.game_id=m.game_id AND r.status='COMPLETED' AND s.mode='COLLECT')
      ORDER BY e.id DESC LIMIT 200""")
    rows=cursor.fetchall()
for snapshot,action in rows:
  snapshot,action=json.loads(snapshot),json.loads(action)
  mapping={'price':'price','productCount':'product_count','quality':'quality',
           'advertisingIntensity':'advertising_intensity'}
  if all(bounds[feature]['min']<=float(action[key])<=bounds[feature]['max'] for key,feature in mapping.items()):
    print(json.dumps({'snapshot':snapshot,'plans':[action,action|{'price':bounds['price']['max']+1000}]}))
    break
else: raise RuntimeError('No suitable action found in recent completed episodes')
'''
PREDICT = '''
import json,sys,urllib.request
payload=json.loads(sys.argv[1])
request=urllib.request.Request('http://localhost:8090/predict',data=json.dumps(payload).encode(),
                              headers={'Content-Type':'application/json'})
print(urllib.request.urlopen(request,timeout=30).read().decode())
'''


def main():
    report = json.loads(docker('exec', 'e-game-ml-1', 'cat', '/app/artifacts/last-report.json'))
    sample = json.loads(docker('exec', '-i', 'e-game-exp-experiment-ml-1', 'python', '-',
                              json.dumps(report['feature_ranges']), code=SAMPLE))
    prediction = json.loads(docker('exec', '-i', 'e-game-ml-1', 'python', '-', json.dumps(sample), code=PREDICT))
    assert prediction['name'] == report['champion'] and prediction['version'] == report['version']
    assert prediction['predictions'][0] is not None
    assert prediction['predictions'][1] is None and prediction['reasons'][1] == 'OUTSIDE_TRAIN_RANGE'
    assert {c['name'] for c in prediction['comparisons']} == {'Ridge', 'CatBoost', 'LightGBM'}
    assert all(c['predictions'][0] is not None and c['predictions'][1] is None for c in prediction['comparisons'])
    print(json.dumps({'model':prediction['name'],'version':prediction['version'],
                      'prediction':prediction['predictions'][0],
                      'comparisons':[c['name'] for c in prediction['comparisons']],
                      'outside_training_range':'correctly rejected'},ensure_ascii=False,indent=2))
    with urllib.request.urlopen('http://localhost:8080/sign-in', timeout=15) as response:
        assert response.status == 200
        print('User application on localhost:8080: HTTP 200')


if __name__ == '__main__':
    main()
