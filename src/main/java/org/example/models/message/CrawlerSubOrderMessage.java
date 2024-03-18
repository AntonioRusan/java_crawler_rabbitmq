package org.example.models.message;

import com.google.gson.annotations.SerializedName;
import org.example.models.KeyValue;

import java.util.List;

public record CrawlerSubOrderMessage(
        @SerializedName("order_id") Long orderId,
        @SerializedName("args") List<KeyValue> args
) implements CrawlerMessage {
}
