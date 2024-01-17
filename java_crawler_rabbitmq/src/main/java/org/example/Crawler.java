package org.example;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    private static final String QUEUE_NAME = "test_crawl_orders";

    public static void main(String[] args) throws IOException, TimeoutException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

        };
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });

        /*String url = "https://demo-site.at.ispras.ru/product/1";
        HtmlPage page = getDocument(url);
        parsePage(page, url);*/
        //System.out.print(page.asNormalizedText());
    }


    private static HtmlPage getDocument(String url) {
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

    private static ProductItem getProductItem(HtmlPage page, String url)
    {
        String productId = page.querySelector("h2").asNormalizedText();

        Pattern pattern = Pattern.compile("^Товар #(\\d+)$");
        Matcher matcher = pattern.matcher(productId);
        if (matcher.matches())
        {
            productId = matcher.group(1);
            System.out.print(matcher.group(1));
        }
        else productId = null;
        String productName = page.querySelector("h5.card-title").asNormalizedText();
        return new ProductItem(productId, productName, url);
    }

    /*private static CrawlerMessage parsePage(HtmlPage page, String url)
    {
        ProductItem productItem = getProductItem(page, url);
        return new CrawlerMessage()
    }*/
}

