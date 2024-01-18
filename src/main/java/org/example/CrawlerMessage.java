package org.example;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public record CrawlerMessage(
        @SerializedName("order_id") String orderId,
        @SerializedName("status") OrderStatus status,
        @SerializedName("result") ProductItem result
) {
    private static final Gson gson = new Gson();

    public String toJson() {
        return gson.toJson(this);
    }

    public static CrawlerMessage fromJson(String json) {
        return gson.fromJson(json, CrawlerMessage.class);
    }
}
