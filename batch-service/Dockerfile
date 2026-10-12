# syntax=docker/dockerfile:1
# ---------- Etapa 1: compilacion (Maven + JDK 17) ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl batch-service -am -DskipTests package

# ---------- Etapa 2: imagen final liviana (solo JRE) ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/batch-output /app/input && chown -R app:app /app
WORKDIR /app
COPY --from=build /workspace/batch-service/target/batch-service-*.jar app.jar
USER app
EXPOSE 8090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
