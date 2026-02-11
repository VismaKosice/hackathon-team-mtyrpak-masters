FROM eclipse-temurin:21-jdk-alpine AS build
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
     "-XX:+UseG1GC", \
     "-Xms1g", "-Xmx1g", \
     "-XX:+UnlockExperimentalVMOptions", \
     "-XX:G1NewSizePercent=60", \
     "-XX:G1MaxNewSizePercent=75", \
     "-XX:MaxGCPauseMillis=10", \
     "-XX:+AlwaysPreTouch", \
     "-XX:+ParallelRefProcEnabled", \
     "-XX:CompileThreshold=500", \
     "-XX:CICompilerCount=2", \
     "-jar", "/app/app.jar"]
