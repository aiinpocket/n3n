FROM node:20-slim

WORKDIR /app

COPY package*.json ./
RUN npm ci --production=false

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build && npm prune --production

ENV PORT=8080
EXPOSE 8080

# Run as non-root user for security
RUN groupadd -r cloudSync && useradd -r -g cloudSync -d /app cloudSync && chown -R cloudSync:cloudSync /app
USER cloudSync

CMD ["npx", "functions-framework", "--target=n3nCloudSync", "--port=8080"]
