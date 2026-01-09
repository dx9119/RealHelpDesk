# ---------- STAGE 1: Build ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# ---------- STAGE 2: Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app/ssl

COPY --from=build /app/target/*.jar app.jar

COPY ./keystore.p12 /app/ssl/keystore.p12

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
