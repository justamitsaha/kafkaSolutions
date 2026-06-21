FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn -q -B -e dependency:go-offline

# Build
COPY src ./src
RUN mvn -q -B -e clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup --system spring && adduser -S spring -G spring
USER spring:spring

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Kolkata"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
