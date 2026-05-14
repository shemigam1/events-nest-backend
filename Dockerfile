FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src ./src

# BuildKit cache mount: ~/.m2 persists across builds (even --no-cache),
# so we only download a dependency once. First build is still slow because
# it has to populate the cache from Maven Central; subsequent builds are
# dramatically faster — even after pom.xml edits, only the new deps are
# fetched. -q dropped so progress is visible.
#
# -Dmaven.test.skip=true (NOT -DskipTests) skips test *compilation*, not
# just test execution. That spares us a chain of test-only deps —
# spring-boot-starter-kafka-test transitively pulls Kafka Streams + RocksDB
# (~70 MB) and Scala, none of which ship inside the runtime image anyway.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -B -Dmaven.test.skip=true package && \
    cp target/*.jar target/app.jar

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --create-home spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app
USER spring:spring

COPY --from=build --chown=spring:spring /workspace/target/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
