# -------- BUILD --------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# -------- RUN --------
FROM gcr.io/distroless/java21-debian12
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]