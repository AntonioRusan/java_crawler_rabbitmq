package org.example;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public record Instruction(
        @SerializedName("order_id") @NotNull Long orderId,
        @SerializedName("url") @NotNull String url
) {
    private static final Gson gson = new Gson();
    public String toJson()
    {
        return gson.toJson(this);
    }
    public static Instruction fromJson(String json)
    {
        return gson.fromJson(json, Instruction.class);
    }
}
