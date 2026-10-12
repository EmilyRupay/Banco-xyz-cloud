# syntax=docker/dockerfile:1
# ---------- Etapa 1: compilacion (Maven + JDK 17) ----------
# El contexto de build es la RAIZ del repositorio (necesita el pom padre).
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl bff-atm -am -DskipTests package

# ---------- Etapa 2: imagen final liviana (solo JRE) ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/bff-atm/target/bff-atm-*.jar app.jar
USER app
EXPOSE 8086
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
