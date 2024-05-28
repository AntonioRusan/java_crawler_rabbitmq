package org.example;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.rabbitmq.client.*;
import org.example.enums.CrawlStatus;
import org.example.models.KeyValue;
import org.example.models.ProductItem;
import org.example.models.message.CrawlRequestMessage;
import org.example.models.message.CrawlerMessage;
import org.example.models.message.CrawlerResultMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Crawler {
    private final String RABBITMQ_HOST; //must be url like "amqp://guest:guest@localhost:5672/%2F"
    private final String RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY; //name of input queue
    private final String RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY; //name of output queue
    private final String RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY; //name of queue for sub requests

    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    public void startCrawler() {
        try {
            Channel channel = getRabbitMQChannel();
            logger.info("Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = getDeliverCallback(channel);
            CancelCallback cancelCallback = getCancelCallback();
            boolean autoAck = false;

            channel.basicConsume(RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY, autoAck, deliverCallback, cancelCallback);
        } catch (Exception ex) {
            logger.error("Exception caught: " + Arrays.toString(ex.getStackTrace()));
            System.exit(1);
        }
    }

    public Crawler(Map<String, Object> argsToValueMap) {
        RABBITMQ_HOST = (String) argsToValueMap.getOrDefault("RABBITMQ_HOST", "amqp://guest:guest@localhost:5672/%2F");
        RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY = (String) argsToValueMap.getOrDefault("RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY", "java_input_crawl_requests");
        RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY = (String) argsToValueMap.getOrDefault("RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY", "java_output_crawl_results");
        RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY = (String) argsToValueMap.getOrDefault("RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY", "java_output_crawl_requests");
    }

    private DeliverCallback getDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            CrawlRequestMessage crawlRequest = CrawlRequestMessage.fromJson(message);
            logger.info(String.format("Received from %s: %s%n", RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY, message));

            try {
                Map<String, String> inputArgs = crawlRequest.args().stream()
                        .collect(Collectors.toMap(KeyValue::key, KeyValue::value));
                String url = inputArgs.getOrDefault("url", "https://demo-site.at.ispras.ru/product/1");
                Long numOfSubRequests = Long.valueOf(inputArgs.getOrDefault("numOfSubRequests", "0"));
                Long numOfSubSubRequests = Long.valueOf(inputArgs.getOrDefault("numOfSubSubRequests", "0"));
                boolean hasError = Boolean.parseBoolean(inputArgs.getOrDefault("hasError", "false"));
                boolean subRequestHasError = Boolean.parseBoolean(inputArgs.getOrDefault("subRequestHasError", "false"));
                Long orderId = crawlRequest.orderId();
                Long crawlRequestId = crawlRequest.crawlRequestId();

                HtmlPage page = getPage(url);

                publishToRabbitMQChannel(
                        channel,
                        RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY,
                        new CrawlerResultMessage(crawlRequestId, orderId, CrawlStatus.Running, 0L, null)
                );

                boolean subRequestErrorNotAdded = true;
                Random r = new Random();
                Long requestsCreated = 0L;
                for (int i = 0; i < numOfSubRequests; i++) {
                    int pageId = r.nextInt(55) + 1;
                    List<KeyValue> subArgs = new ArrayList<>() {{
                        add(new KeyValue("url", String.format("https://demo-site.at.ispras.ru/product/%d", pageId)));
                        add(new KeyValue("numOfSubRequests", numOfSubSubRequests.toString()));
                    }};
                    if (subRequestHasError && subRequestErrorNotAdded) {
                        subArgs.add(new KeyValue("hasError", "true"));
                        subRequestErrorNotAdded = false;
                    }
                    publishToRabbitMQChannel(
                            channel,
                            RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY,
                            new CrawlRequestMessage(crawlRequestId, orderId, subArgs));
                    requestsCreated++;
                }
                if (hasError) {
                    CrawlerResultMessage errorMessage = new CrawlerResultMessage(crawlRequestId, orderId, CrawlStatus.Error, requestsCreated, null);
                    publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY, errorMessage);
                } else {
                    CrawlerResultMessage finishMessage = parsePage(page, crawlRequestId, orderId, url, requestsCreated);
                    publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY, finishMessage);
                }

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception ex) {
                logger.error("Exception while handling input message: " + ex.getMessage());
                publishToRabbitMQChannel(
                        channel,
                        RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY,
                        new CrawlerResultMessage(
                                crawlRequest.crawlRequestId(),
                                crawlRequest.orderId(),
                                CrawlStatus.Error,
                                0L,
                                null
                        )
                );
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }

        };
    }

    private CancelCallback getCancelCallback() {
        return consumerTag -> {
        };
    }

    private @NotNull Channel getRabbitMQChannel() throws IOException, TimeoutException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RABBITMQ_HOST);

        Connection connection = factory.newConnection();

        Channel channel = connection.createChannel();
        channel.basicQos(1);
        Map<String, Object> queueArguments = new HashMap<>() {{
            put("x-max-priority", 4);
        }};
        boolean durable = true;

        channel.queueDeclare(RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY, durable, false, false, queueArguments);
        channel.queueDeclare(RABBITMQ_OUTPUT_CRAWL_RESULT_QUEUE_KEY, durable, false, false, queueArguments);
        channel.queueDeclare(RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY, durable, false, false, queueArguments);

        return channel;
    }

    private void publishToRabbitMQChannel(Channel channel, String queueKey, CrawlerMessage crawlerMessage) {
        try {
            String crawlerMessageJson = crawlerMessage.toJson();
            channel.basicPublish("", queueKey, null, crawlerMessageJson.getBytes(StandardCharsets.UTF_8));
            logger.info(String.format("Sent to %s: %s%n", queueKey, crawlerMessageJson));
        } catch (IOException ex) {
            logger.error(String.format("Exception during sending to RabbitMQ queue %s: %s%n", queueKey, ex.getMessage()));
        }
    }


    private HtmlPage getPage(String url) throws IOException, InterruptedException {
        HtmlPage page;
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false);
            page = webClient.getPage(url);
        } catch (IOException ex) {
            throw ex;
        }
        Random r = new Random();
        int delay = r.nextInt(500 - 50) + 50;
        Thread.sleep(delay);
        return page;
    }

    private ProductItem getProductItem(HtmlPage page, String url) {
        try {
            String productId = page.querySelector("h2").asNormalizedText();
            Pattern pattern = Pattern.compile("^Товар #(\\d+)$");
            Matcher matcher = pattern.matcher(productId);
            if (matcher.matches()) {
                productId = matcher.group(1);
            } else productId = null;
            String productName = page.querySelector("h5.card-title").asNormalizedText();
            return new ProductItem(productId, productName, url);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    private CrawlerResultMessage parsePage(HtmlPage page, Long crawlRequestId, Long orderId, String url, Long requestsCreated) {
        ProductItem productItem = getProductItem(page, url);
        return new CrawlerResultMessage(crawlRequestId, orderId, CrawlStatus.Finished, requestsCreated, productItem);
    }

    private String getEnvOrElse(@NotNull String envVariableName, String alternativeValue) {
        String envValue = System.getenv(envVariableName);
        return envValue != null ? envValue : alternativeValue;
    }

}

