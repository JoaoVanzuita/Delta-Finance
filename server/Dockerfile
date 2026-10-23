# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace

# Dependências primeiro, para aproveitar cache de camadas.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:25.0.4.1_1-jre AS runtime

# curl é necessário apenas para o healthcheck do Compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/target/delta-finance-server-*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENV TZ=America/Sao_Paulo
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
