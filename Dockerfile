FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies 2>/dev/null || true
COPY src/ src/
RUN ./gradlew --no-daemon jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/pension-engine.jar /app/app.jar
EXPOSE 8080 9090
CMD ["java", \
     "-XX:+UseParallelGC", \
     "-Xms2g", "-Xmx2g", \
     "-XX:+AlwaysPreTouch", \
     "-XX:+ParallelRefProcEnabled", \
     "-XX:CompileThreshold=500", \
     "-XX:CICompilerCount=2", \
     "-jar", "/app/app.jar"]
