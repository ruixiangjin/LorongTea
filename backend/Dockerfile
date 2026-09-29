FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:4d271cd5e0624598cf563342f47281b09cb364bc13acbbd7251f49f83470018d AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip=6.0-28ubuntu4.1 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -Dmaven.test.skip=true package && cp target/lorong-tea-*.jar /lorong-tea.jar

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:7739f0ffce786528961eea6bf46d9610ee968ac6127c9b2e93494757bdecce9f
WORKDIR /app
COPY --from=build /lorong-tea.jar ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/lorong-tea.jar"]
