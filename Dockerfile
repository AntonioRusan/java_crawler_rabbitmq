FROM openjdk:21

COPY java-rabbitmq-crawler-0.1.0-jar-with-dependencies.jar home/app/crawler.jar


ENTRYPOINT [ "java", "-jar", "/home/app/crawler.jar"]

# Settings for rabbitmq, should be set in docker-compose.yaml or k8s yaml
#CMD ["-s", "RABBITMQ_HOST=amqp://guest:guest@localhost:5672/%2F", "-s", "RABBITMQ_INPUT_QUEUE_KEY=java_crawl_orders", "-s", "RABBITMQ_OUTPUT_QUEUE_KEY=java_crawl_results", "-s", "RABBITMQ_SUB_ORDERS_QUEUE_KEY=java_crawl_sub_orders"]