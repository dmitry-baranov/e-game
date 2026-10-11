"""Check the local Grafana source and every provisioned panel without printing secrets."""
import base64
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
values = dict(line.split('=', 1) for line in (ROOT / '.env.analytics').read_text().splitlines()
              if line and not line.startswith('#') and '=' in line)
base = 'http://127.0.0.1:' + values.get('GRAFANA_PORT', '3000')
token = base64.b64encode(('admin:' + values['GRAFANA_ADMIN_PASSWORD']).encode()).decode()


def request(path, body=None):
    headers = {'Authorization': 'Basic ' + token}
    payload = None
    if body is not None:
        headers['Content-Type'] = 'application/json'
        payload = json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=payload, headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)


def query(sql):
    data = request('/api/ds/query', {'from': '0', 'to': 'now', 'queries': [{
        'refId': 'A', 'datasource': {'type': 'postgres', 'uid': 'experiment-postgres'},
        'format': 'table', 'rawSql': sql, 'rawQuery': True, 'intervalMs': 1000,
        'maxDataPoints': 1000}]})
    result = data['results']['A']
    if result.get('error'):
        raise RuntimeError(result['error'])
    return result.get('frames', [])


health = request('/api/health')
assert health['database'] == 'ok'
source = request('/api/datasources/uid/experiment-postgres/health')
assert source['status'] == 'OK', source
dashboard = request('/api/dashboards/uid/experiment-training')['dashboard']
frames = query('SELECT training_run_id::text FROM training_run_corpus ORDER BY created_at DESC LIMIT 1')
run_id = frames[0]['data']['values'][0][0]
for panel in dashboard['panels']:
    rows = 0
    for target in panel['targets']:
        frames = query(target['rawSql'].replace('$training_run_id', run_id))
        rows += sum(len(f['data']['values'][0]) for f in frames if f['data'].get('values'))
    print(f"Panel {panel['id']}: OK, {rows} rows — {panel['title']}")
print(f'Grafana {health["version"]}: healthy; PostgreSQL connected; all panels checked.')
