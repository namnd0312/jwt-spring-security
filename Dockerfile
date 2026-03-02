FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/spring-jwt.jar spring-jwt.jar
ENTRYPOINT ["java", "-jar", "spring-jwt.jar"]
