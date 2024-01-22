package org.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Publisher {
    private final static String EXCHANGE_NAME = "crawler_exchange";
    private final static String QUEUE_NAME = "java_crawl_orders";
    private final static String QUEUE_BINDING = "java_crawl_orders.*";
    private final static String QUEUE_KEY = "java_crawl_orders.1";
    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            Map<String, Object> arguments = new HashMap<>() {{
                put("x-max-priority", 4);
            }};
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            channel.queueDeclare(QUEUE_NAME, true, false, false, arguments);
            channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, QUEUE_BINDING);

            for (int i = 1; i < 3; i++) {
                Instruction instruction = new Instruction((long) i, "https://demo-site.at.ispras.ru/product/" + i);
                String instructionMessage = instruction.toJson();
                channel.basicPublish(EXCHANGE_NAME, QUEUE_KEY, null, instructionMessage.getBytes(StandardCharsets.UTF_8));
                System.out.printf("Sent to %s: %s%n", QUEUE_NAME, instructionMessage);
            }
        }
    }
}
