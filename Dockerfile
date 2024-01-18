#FROM openjdk:21

#COPY target/java_crawler_rabbitmq-1.0-SNAPSHOT.jar app.jar
#ENTRYPOINT ["java","-jar","/app.jar"]


FROM maven:latest AS build
# Set the working directory in the container
WORKDIR /app
# Copy the pom.xml and the project files to the container
COPY pom.xml .
COPY src ./src
# Build the application using Maven
RUN mvn clean package -DskipTests

# Use an official OpenJDK image as the base image
FROM openjdk:11-jre-slim
# Set the working directory in the container
WORKDIR /app
# Copy the built JAR file from the previous stage to the container
COPY target/java_crawler_rabbitmq-1.0-SNAPSHOT.jar app.jar
# Set the command to run the application
CMD ["java", "-jar", "app.jar"]