FROM eclipse-temurin:21-jre-jammy

ARG LAVALINK_VERSION=4.2.2
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl python3 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/logs \
    && curl -fsSL "https://github.com/lavalink-devs/Lavalink/releases/download/${LAVALINK_VERSION}/Lavalink.jar" -o /app/Lavalink.jar

COPY application.yml /app/application.yml
COPY scripts/start-shardcloud.py /app/scripts/start-shardcloud.py
COPY scripts/panel.py /app/scripts/panel.py
COPY scripts/panel.html /app/scripts/panel.html

EXPOSE 80
CMD ["python3", "/app/scripts/start-shardcloud.py"]
