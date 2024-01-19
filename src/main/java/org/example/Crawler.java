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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Crawler {
    private static final String RABBITMQ_HOST = getEnvOrElse("RABBITMQ_HOST","amqp://guest:guest@rabbitmq:5672/%2F");
    private static final String RABBITMQ_INPUT_QUEUE_KEY = getEnvOrElse("RABBITMQ_INPUT_QUEUE_KEY", "test_crawl_orders");
    private static final String RABBITMQ_OUTPUT_QUEUE_KEY = getEnvOrElse("RABBITMQ_OUTPUT_QUEUE_KEY", "test_crawl_results");
    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    public static void main(String[] args) {
        try {
            Channel channel = getRabbitMQChannel();
            logger.info("Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = getDeliverCallback(channel);
            CancelCallback cancelCallback = getCancelCallback();
            boolean autoAck = false;

            channel.basicConsume(RABBITMQ_INPUT_QUEUE_KEY, autoAck, deliverCallback, cancelCallback);
        } catch (Exception ex) {
            logger.error("Exception caught: " + ex.getMessage());
            System.exit(1);
        }

    }

    private static DeliverCallback getDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Instruction instruction = Instruction.fromJson(message);
            logger.info(String.format("Received from %s: %s%n", RABBITMQ_INPUT_QUEUE_KEY, message));

            try {
                HtmlPage page = getPage(instruction.url());

                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, new CrawlerMessage(instruction.orderId().toString(), OrderStatus.Running, null));

                CrawlerMessage finishMessage = parsePage(page, instruction.orderId().toString(), instruction.url());
                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, finishMessage);

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception ex) {
                logger.error("Exception while handling input message: " + ex.getMessage());
                publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, new CrawlerMessage(instruction.orderId().toString(), OrderStatus.Error, null));
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }

        };
    }

    private static CancelCallback getCancelCallback() {
        return consumerTag -> {
        };
    }

    private static @NotNull Channel getRabbitMQChannel() throws IOException, TimeoutException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RABBITMQ_HOST);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.basicQos(1);
        Map<String, Object> arguments = new HashMap<>() {{
            put("x-max-priority", 4);
        }};
        boolean durable = true;
        channel.queueDeclare(RABBITMQ_INPUT_QUEUE_KEY, durable, false, false, arguments);
        channel.queueDeclare(RABBITMQ_OUTPUT_QUEUE_KEY, durable, false, false, arguments);
        return channel;
    }

    private static void publishToRabbitMQChannel(Channel channel, String queueKey, CrawlerMessage crawlerMessage) {
        try {
            String crawlerMessageJson = crawlerMessage.toJson();
            channel.basicPublish("", queueKey, null, crawlerMessageJson.getBytes(StandardCharsets.UTF_8));
            logger.info(String.format("Sent to %s: %s%n", queueKey, crawlerMessageJson));
        } catch (IOException ex) {
            logger.error(String.format("Exception during sending to RabbitMQ queue %s: %s%n", queueKey, ex.getMessage()));
        }
    }


    private static HtmlPage getPage(String url) throws IOException {
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

    private static ProductItem getProductItem(HtmlPage page, String url) {
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

    private static CrawlerMessage parsePage(HtmlPage page, String orderId, String url) {
        ProductItem productItem = getProductItem(page, url);
        return new CrawlerMessage(orderId, OrderStatus.Finished, productItem);
    }

    private static String getEnvOrElse(@NotNull String envVariableName, String alternativeValue) {
        String envValue = System.getProperty(envVariableName);
        return envValue != null ? envValue : alternativeValue;
    }

}

