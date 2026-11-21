FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/logs \
    && chown 10001:10001 /app/logs

COPY --chown=10001:10001 target/trazalab-*.jar /app/trazalab.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/trazalab.jar"]
