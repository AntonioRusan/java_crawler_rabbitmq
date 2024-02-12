#!/bin/bash

# Prepare Jar
mvn -f pom.xml clean package -DskipTests=true

# Move jar from target/ to current folder
cp ./target/java-rabbitmq-crawler-0.1.0-jar-with-dependencies.jar ./java-rabbitmq-crawler-0.1.0-jar-with-dependencies.jar