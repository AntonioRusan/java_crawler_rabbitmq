package org.example;

import com.google.gson.annotations.SerializedName;

public record ProductItem(
        @SerializedName("product_id") String productId,
        @SerializedName("product_name") String productName,
        @SerializedName("url") String url
) {
}
