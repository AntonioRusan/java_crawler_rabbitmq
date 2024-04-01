package org.example.models.message;

import com.google.gson.annotations.SerializedName;
import org.example.models.KeyValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record CrawlRequestMessage(
        @SerializedName("crawl_request_id") @NotNull Long crawlRequestId,
        @SerializedName("order_id") Long orderId,
        @SerializedName("args") List<KeyValue> args
) implements CrawlerMessage {
    public static CrawlRequestMessage fromJson(String json) {
        return gson.fromJson(json, CrawlRequestMessage.class);
    }
}
