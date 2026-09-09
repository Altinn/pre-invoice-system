# Multi-stage build. Stage 1 builds the fat jar with the Maven wrapper against a Temurin 21 JDK;
# stage 2 runs it on a slim Temurin 21 JRE (docs/02). No Azure anything — runs on any container host.

# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache dependencies first: copy only what the wrapper + pom need, resolve offline-able deps.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# Then build.
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

# Non-root runtime user, plus a writable archive directory it owns. Exports (LG04/PDF/CSV/XLSX)
# are written here by LocalFileArchive; without this the non-root user cannot create /app/data.
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/data/arkiv && chown -R app:app /app/data
USER app

COPY --from=build --chown=app:app /workspace/target/forsystem-*.jar app.jar

# Default archive location inside the container (overridable via env).
ENV FORSYSTEM_ARKIV_KATALOG=/app/data/arkiv

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
