package org.example.models.message;

import com.google.gson.annotations.SerializedName;
import org.example.enums.OrderStatus;
import org.example.models.ProductItem;
import org.jetbrains.annotations.NotNull;

public record CrawlerResultMessage(
        @SerializedName("crawl_request_id") @NotNull Long crawlRequestId,
        @SerializedName("order_id") Long orderId,
        @SerializedName("status") OrderStatus status,
        @SerializedName("result") ProductItem result
) implements CrawlerMessage {
    public static CrawlerResultMessage fromJson(String json) {
        return gson.fromJson(json, CrawlerResultMessage.class);
    }
}
