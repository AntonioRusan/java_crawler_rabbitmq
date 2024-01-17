package org.example;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    private static final String RABBITMQ_INPUT_QUEUE_KEY = "test_crawl_orders";
    private static final String RABBITMQ_OUTPUT_QUEUE_KEY = "test_crawl_results";

    public static void main(String[] args) throws IOException, TimeoutException {

        Channel channel = getRabbitMQChannel();

        System.out.println("Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {

            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            Instruction instruction = Instruction.fromJson(message);

            System.out.printf("Received from %s: %s%n", RABBITMQ_INPUT_QUEUE_KEY, message);

            HtmlPage page = getPage(instruction.url());

            publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, new CrawlerMessage(instruction.orderId().toString(), "Running", null));

            CrawlerMessage finishMessage = parsePage(page, instruction.orderId().toString(), instruction.url());
            publishToRabbitMQChannel(channel, RABBITMQ_OUTPUT_QUEUE_KEY, finishMessage);

            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

        };
        boolean autoAck = false;
        channel.basicConsume(RABBITMQ_INPUT_QUEUE_KEY, autoAck, deliverCallback, consumerTag -> {
        });
    }

    private static @NotNull Channel getRabbitMQChannel() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
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
            System.out.printf("Sent to %s: %s%n", queueKey, crawlerMessageJson);
        } catch (IOException ex) {
            System.out.printf("Exception during sending to RabbitMQ queue %s: %s%n", queueKey, ex.getMessage());
        }
    }


    private static HtmlPage getPage(String url) {
        HtmlPage page = null;
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false);
            page = webClient.getPage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return page;
    }

    private static ProductItem getProductItem(HtmlPage page, String url) {
        String productId = page.querySelector("h2").asNormalizedText();

        Pattern pattern = Pattern.compile("^Товар #(\\d+)$");
        Matcher matcher = pattern.matcher(productId);
        if (matcher.matches()) {
            productId = matcher.group(1);
        } else productId = null;
        String productName = page.querySelector("h5.card-title").asNormalizedText();
        return new ProductItem(productId, productName, url);
    }

    private static CrawlerMessage parsePage(HtmlPage page, String orderId, String url) {
        ProductItem productItem = getProductItem(page, url);
        return new CrawlerMessage(orderId, "Finished", productItem);
    }
}

