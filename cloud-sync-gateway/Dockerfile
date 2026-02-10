FROM node:20-slim

WORKDIR /app

COPY package*.json ./
RUN npm ci --production=false

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build && npm prune --production

ENV PORT=8080
EXPOSE 8080

CMD ["npx", "functions-framework", "--target=n3nCloudSync", "--port=8080"]
