FROM openjdk:21

COPY java-rabbitmq-crawler-0.1.0-jar-with-dependencies.jar home/app/crawler.jar


ENTRYPOINT [ "java", "-jar", "/home/app/crawler.jar"]

# Settings for rabbitmq, should be set in docker-compose.yaml or k8s yaml
#CMD ["-s", "RABBITMQ_HOST=amqp://guest:guest@localhost:5672/%2F", "-s", "RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY=java_input_crawl_requests", "-s", "RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY=java_output_crawl_results", "-s", "RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY=java_output_crawl_requests"]