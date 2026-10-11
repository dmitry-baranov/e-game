FROM maven:3.10.0-eclipse-temurin-25 AS build

WORKDIR /app

COPY .mvn .mvn
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/diploma-0.1.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
