#!/bin/bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:-local-hardware}"
SERVICE_NAME="n3n-cloud-sync"
REGION="${GCP_REGION:-asia-east1}"
BUCKET_NAME="${BUCKET_NAME:-n3n-public}"

echo "=== N3N Cloud Sync Gateway Deploy (Cloud Run) ==="
echo "Project:  ${PROJECT_ID}"
echo "Region:   ${REGION}"
echo "Service:  ${SERVICE_NAME}"
echo "Bucket:   ${BUCKET_NAME}"
echo ""

# Deploy to Cloud Run (source-based, auto builds with Cloud Build)
echo "Deploying to Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --source=. \
  --allow-unauthenticated \
  --memory=256Mi \
  --timeout=60s \
  --max-instances=10 \
  --set-env-vars="BUCKET_NAME=${BUCKET_NAME}" \
  --quiet

# Get the service URL
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" \
  --project="${PROJECT_ID}" --region="${REGION}" \
  --format='value(status.url)')

echo ""
echo "=== Deploy Complete ==="
echo "URL: ${SERVICE_URL}"
echo "Health: ${SERVICE_URL}/health"
