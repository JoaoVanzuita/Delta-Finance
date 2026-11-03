# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
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
FROM eclipse-temurin:25.0.4.1_1-jre@sha256:78f1f183913482ef390c2722f46c3f0ce76ada341d69590e7c482ec943ee72d5 AS runtime

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
