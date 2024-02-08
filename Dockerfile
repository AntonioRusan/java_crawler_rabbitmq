#
# Build stage
#
FROM maven:3.9.4-amazoncorretto-21-debian AS build

# Copy the pom.xml and the project files to the container
COPY pom.xml /home/app/pom.xml
COPY src /home/app/src

# Build the application using Maven
RUN mvn -f /home/app/pom.xml clean package -DskipTests=true


#
# Package stage
#
# Use an official OpenJDK image as the base image
FROM amazoncorretto:21.0.1

# Set the entrypoint to run the application
ENTRYPOINT [ "java", "-jar", "/home/app/crawler.jar" ]

# Copy the built JAR file from the previous stage to the container
COPY --from=build /home/app/target/rabbitmq-crawler-0.1.0-jar-with-dependencies.jar /home/app/crawler.jar
