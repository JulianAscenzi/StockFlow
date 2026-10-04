FROM eclipse-temurin:21-jdk-jammy@sha256:ce5767b7222312d42395f5bab033cd91f09e44032a2f21bdfd7b5b912dbe1e77 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
ARG SOURCE_DATE_EPOCH=315532800
RUN chmod +x mvnw && ./mvnw package -DskipTests -Dproject.build.outputTimestamp=${SOURCE_DATE_EPOCH}

FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home stockflow
USER stockflow
WORKDIR /app
COPY --from=build --chown=stockflow:stockflow /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
