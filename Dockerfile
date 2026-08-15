ARG BASE_IMAGE=amazoncorretto:21-alpine-jdk
FROM ${BASE_IMAGE}

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ARG IMAGE_VERSION=1.0.0
LABEL org.opencontainers.image.authors="Manokaran" \
 org.opencontainers.image.vesion="${IMAGE_VERSION}" \
 org.opencontainers.image.title="Users Microservice" \
 org.opencontainers.image.description="Spring Boot service to manage users"

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
CMD ["--spring.profiles.active=test"]