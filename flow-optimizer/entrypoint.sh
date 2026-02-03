#!/bin/bash
set -e

echo "Starting Flow Optimizer Service..."
echo "Model: Phi-3-Mini-4K-Instruct Q4"
echo "Host: ${LLAMAFILE_HOST:-0.0.0.0}"
echo "Port: ${LLAMAFILE_PORT:-8081}"
echo "Threads: ${LLAMAFILE_THREADS:-4}"
echo "Context Size: ${LLAMAFILE_CTX_SIZE:-4096}"

exec /app/llamafile \
    --server \
    --host "${LLAMAFILE_HOST:-0.0.0.0}" \
    --port "${LLAMAFILE_PORT:-8081}" \
    -m /models/phi-3-mini-4k-instruct-q4.gguf \
    -c "${LLAMAFILE_CTX_SIZE:-4096}" \
    -t "${LLAMAFILE_THREADS:-4}" \
    --log-disable
