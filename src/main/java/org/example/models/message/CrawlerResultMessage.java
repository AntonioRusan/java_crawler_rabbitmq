package org.example.models.message;

import com.google.gson.annotations.SerializedName;
import org.example.enums.CrawlStatus;
import org.example.models.ProductItem;
import org.jetbrains.annotations.NotNull;

public record CrawlerResultMessage(
        @SerializedName("crawl_request_id") @NotNull Long crawlRequestId,
        @SerializedName("order_id") Long orderId,
        @SerializedName("status") CrawlStatus status,
        @SerializedName("created_crawl_requests") Long createdCrawlRequests,
        @SerializedName("result") ProductItem result
) implements CrawlerMessage {
    public static CrawlerResultMessage fromJson(String json) {
        return gson.fromJson(json, CrawlerResultMessage.class);
    }
}
