# Single container: Angular UI served by the Spring Boot backend on one origin.
FROM node:22-alpine AS web
WORKDIR /web
ENV NG_CLI_ANALYTICS=false
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/frontend/browser ./src/main/resources/static
RUN mvn -B -q package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=api /api/target/pipeline-failure-explainer-*.jar app.jar
USER app
ENV SERVER_ADDRESS=0.0.0.0 \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
