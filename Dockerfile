FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN mvn --batch-mode -pl "${SERVICE}" -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S campusmind && adduser -S campusmind -G campusmind
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*.jar /app/app.jar
USER campusmind
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
