FROM python:3.13-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
COPY ml/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY ml/*.py ./
COPY ml/*.sql ./
ENV STRATEGY_ARTIFACT=/app/artifacts/champion.joblib STRATEGY_AUTO_TRAIN=true
CMD ["gunicorn", "-w", "1", "-b", "0.0.0.0:8090", "--timeout", "60", "server:app"]
