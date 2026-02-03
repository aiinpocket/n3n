# Flow Optimizer Service
# Uses llamafile + Phi-3-Mini for local AI flow optimization
FROM debian:bookworm-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create directories
RUN mkdir -p /app /models

WORKDIR /app

# Download llamafile (using a specific stable version)
ARG LLAMAFILE_VERSION=0.8.13
RUN curl -L -o /app/llamafile \
    "https://github.com/Mozilla-Ocho/llamafile/releases/download/${LLAMAFILE_VERSION}/llamafile-${LLAMAFILE_VERSION}" && \
    chmod +x /app/llamafile

# Download Phi-3-Mini-4K-Instruct Q4_K_M model (~2.3GB)
# This is a high-quality quantized version optimized for CPU inference
ARG MODEL_URL="https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
RUN curl -L -o /models/phi-3-mini-4k-instruct-q4.gguf "${MODEL_URL}"

# Copy startup script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Expose port for API
EXPOSE 8081

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8081/health || exit 1

# Set environment variables
ENV LLAMAFILE_HOST=0.0.0.0
ENV LLAMAFILE_PORT=8081
ENV LLAMAFILE_THREADS=4
ENV LLAMAFILE_CTX_SIZE=4096

ENTRYPOINT ["/app/entrypoint.sh"]
