package org.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.example.models.KeyValue;
import org.example.models.message.CrawlRequestMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Publisher {
    private final static String QUEUE_KEY = "java_crawl_requests";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            Map<String, Object> arguments = new HashMap<>() {{
                put("x-max-priority", 4);
            }};
            channel.queueDeclare(QUEUE_KEY, true, false, false, arguments);

            for (int i = 1; i < 3; i++) {
                String url = "https://demo-site.at.ispras.ru/product/" + i;
                CrawlRequestMessage crawlRequest = new CrawlRequestMessage(
                        (long) i,
                        (long) i,
                        List.of(
                                new KeyValue("url", url),
                                new KeyValue("createSubRequests", String.valueOf(true))
                        )
                );
                String crawlRequestJson = crawlRequest.toJson();
                channel.basicPublish("", QUEUE_KEY, null, crawlRequestJson.getBytes(StandardCharsets.UTF_8));
                System.out.printf("Sent to %s: %s%n", QUEUE_KEY, crawlRequestJson);
            }
        }
    }
}
