package org.example;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.rabbitmq.client.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Crawler {
    private final String RABBITMQ_HOST; //must be url like "amqp://guest:guest@localhost:5672/%2F"
    private final String RABBITMQ_INPUT_QUEUE_KEY; //name of input queue
    private final String RABBITMQ_OUTPUT_QUEUE_KEY; //name of output queue

    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    public void startCrawler() {
        try {
            Channel channel = getRabbitMQChannel();
            logger.info("Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = getDeliverCallback(channel);
            CancelCallback cancelCallback = getCancelCallback();
            boolean autoAck = false;

            channel.basicConsume(RABBITMQ_INPUT_QUEUE_KEY, autoAck, deliverCallback, cancelCallback);
        } catch (Exception ex) {
            logger.error("Exception caught: " + Arrays.toString(ex.getStackTrace()));
            System.exit(1);
        }
    }

    public Crawler(Map<String, Object> argsToValueMap) {
        RABBITMQ_HOST = (String) argsToValueMap.getOrDefault("RABBITMQ_HOST", "amqp://guest:guest@localhost:5672/%2F");
        RABBITMQ_INPUT_QUEUE_KEY = (String) argsToValueMap.getOrDefault("RABBITMQ_INPUT_QUEUE_KEY", "java_crawl_orders");
        RABBITMQ_OUTPUT_QUEUE_KEY = (String) argsToValueMap.getOrDefault("RABBITMQ_OUTPUT_QUEUE_KEY", "java_crawl_results");
    }

    private DeliverCallback getDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Instruction instruction = Instruction.fromJson(message);
            logger.info(String.format("Received from %s: %s%n", RABBITMQ_INPUT_QUEUE_KEY, message));

            try {
                HtmlPage page = getPage(instruction.url());

                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, new CrawlerMessage(instruction.orderId().toString(), OrderStatus.Running, null));

                CrawlerMessage finishMessage = parsePage(page, instruction.orderId().toString(), instruction.url());
                Thread.sleep(3000);
                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, finishMessage);

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception ex) {
                logger.error("Exception while handling input message: " + ex.getMessage());
                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, new CrawlerMessage(instruction.orderId().toString(), OrderStatus.Error, null));
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

        channel.queueDeclare(RABBITMQ_INPUT_QUEUE_KEY, durable, false, false, queueArguments);
        channel.queueDeclare(RABBITMQ_OUTPUT_QUEUE_KEY, durable, false, false, queueArguments);

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


    private HtmlPage getPage(String url) throws IOException {
        HtmlPage page = null;
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false);
            page = webClient.getPage(url);
        } catch (IOException ex) {
            throw ex;
        }
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
            throw ex;
        }

    }

    private CrawlerMessage parsePage(HtmlPage page, String orderId, String url) {
        ProductItem productItem = getProductItem(page, url);
        return new CrawlerMessage(orderId, OrderStatus.Finished, productItem);
    }

    private String getEnvOrElse(@NotNull String envVariableName, String alternativeValue) {
        String envValue = System.getenv(envVariableName);
        return envValue != null ? envValue : alternativeValue;
    }

}

