# Stage 1 — Backend build
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build
COPY packages/backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY packages/backend/src ./src
RUN mvn package -DskipTests -B

# Stage 2 — Frontend build
FROM node:20-alpine AS frontend-build
RUN corepack enable && corepack prepare pnpm@9 --activate
WORKDIR /build
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY packages/frontend/package.json ./packages/frontend/
RUN pnpm install --frozen-lockfile || pnpm install
COPY packages/frontend/ ./packages/frontend/
RUN pnpm --filter @plantuml-viewer/frontend build

# Stage 3 — Runtime
FROM eclipse-temurin:17-jre
RUN apt-get update && \
    apt-get install -y --no-install-recommends nginx supervisor graphviz && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy frontend dist to Nginx
COPY --from=frontend-build /build/packages/frontend/dist /usr/share/nginx/html

# Copy Nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Remove default Nginx config that may conflict
RUN rm -f /etc/nginx/sites-enabled/default

# Copy backend JAR
COPY --from=backend-build /build/target/viewer-backend-1.0.0.jar /app/viewer-backend.jar

# Supervisor config
RUN mkdir -p /var/log/supervisor
COPY docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 80

CMD ["supervisord", "-c", "/etc/supervisor/supervisord.conf"]
