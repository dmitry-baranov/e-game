"""Start the e-game experimental dashboard; credentials stay in .env.analytics.

Run from anywhere: python3 observability/grafana/start_grafana.py
Only the e-game-exp database role and Grafana service are configured.
"""
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / '.env.analytics'
CONTAINER = 'e-game-exp-experiment-db-1'


def run(args, **kwargs):
    return subprocess.run(args, check=True, cwd=ROOT, **kwargs)


def main():
    values = {}
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                values[key.strip()] = value.strip()
    for key in ('GRAFANA_ADMIN_PASSWORD', 'GRAFANA_DB_READ_PASSWORD'):
        if not values.get(key):
            values[key] = secrets.token_urlsafe(24)
    values.setdefault('GRAFANA_PORT', '3000')
    # Exclude credentials from Git, and do not print them or place them in command arguments.
    descriptor = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        output.write('# Local e-game analytics credentials. Do not commit.\n')
        output.writelines(f'{key}={value}\n' for key, value in values.items())
    ENV_FILE.chmod(0o600)
    password = values['GRAFANA_DB_READ_PASSWORD'].replace("'", "''")
    sql = f"""
DO $setup$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='grafana_reader') THEN
    CREATE ROLE grafana_reader LOGIN;
  END IF;
END $setup$;
ALTER ROLE grafana_reader LOGIN PASSWORD '{password}';
GRANT CONNECT ON DATABASE experiments TO grafana_reader;
GRANT USAGE ON SCHEMA public TO grafana_reader;
GRANT SELECT ON training_run_report, training_run_models, training_run_corpus,
  training_run_slices, training_run_feature_ranges, experiment_evaluation_pairs,
  experiment_fallback_reasons TO grafana_reader;
"""
    # stderr can contain SQL text on failure, so do not echo it with the password.
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'psql', '-U', 'postgres',
                             '-d', 'experiments', '-v', 'ON_ERROR_STOP=1'],
                            input=sql, text=True, capture_output=True, cwd=ROOT)
    if result.returncode:
        raise RuntimeError('Could not configure Grafana database role. Check that experimental DB and training views exist.')
    run(['docker', 'compose', '--env-file', str(ENV_FILE), '-p', 'e-game-exp',
         '-f', 'compose.experiment.yml', '-f', 'compose.analytics.yml',
         'up', '-d', '--no-deps', 'grafana'])
    print(f"Grafana: http://localhost:{values['GRAFANA_PORT']}/d/experiment-training")
    print('Login: admin. Password: GRAFANA_ADMIN_PASSWORD in .env.analytics.')


if __name__ == '__main__':
    main()
