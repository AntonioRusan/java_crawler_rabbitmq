package org.example;

public record CrawlerMessage (
        String orderId,
        String status,
        ProductItem result
){
}
