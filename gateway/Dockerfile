# syntax=docker/dockerfile:1
# ---------- Etapa 1: compilacion (Maven + JDK 17) ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl gateway -am -DskipTests package

# ---------- Etapa 2: certificado TLS autofirmado (solo para desarrollo/demo) ----------
# En AWS se reemplaza por un certificado de ACM en el Load Balancer o se monta uno propio en /app/certs.
FROM eclipse-temurin:17-jdk-alpine AS certs
ARG KEYSTORE_PASSWORD=changeit
RUN keytool -genkeypair -alias gateway -keyalg RSA -keysize 2048 -validity 365 \
      -storetype PKCS12 -keystore /gateway.p12 -storepass "$KEYSTORE_PASSWORD" \
      -dname "CN=localhost, OU=Banco XYZ, O=Duoc UC, L=Santiago, C=CL" \
      -ext "SAN=dns:localhost,dns:gateway,ip:127.0.0.1"

# ---------- Etapa 3: imagen final liviana (solo JRE) ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/certs
WORKDIR /app
COPY --from=build /workspace/gateway/target/gateway-*.jar app.jar
COPY --from=certs /gateway.p12 /app/certs/gateway.p12
RUN chown -R app:app /app
USER app
EXPOSE 8080 8443
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
