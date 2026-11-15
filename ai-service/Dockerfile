FROM python:3.11-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
    ASTRA_DATA_DIR=/data ASTRA_ARTIFACT_DIR=/app/artifacts

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY tests ./tests

EXPOSE 8000
# Models are trained from /data/raw on first start if no artifact exists.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
