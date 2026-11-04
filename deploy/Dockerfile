# [despliegue] Imagen del backend: Maven + JDK 17 compila; JRE 17 corre el .jar sin root.
# Se construye desde la raíz del repo: docker compose usa context: .. (ver docker-compose.yml).

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml lombok.config checkstyle-suppressions.xml spotbugs-exclude.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
# Las pruebas y los analizadores corren en desarrollo (mvn verify); aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B package -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true \
    && cp target/*.jar /src/app.jar

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system nexus && useradd --system --gid nexus --home /app nexus
WORKDIR /app
COPY --from=build /src/app.jar app.jar
# La foto de perfil y los logs se escriben aquí; en docker-compose son volúmenes.
RUN mkdir -p /app/images /app/logs && chown -R nexus:nexus /app
USER nexus
ENV TZ=America/Mexico_City \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -Duser.timezone=America/Mexico_City"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
