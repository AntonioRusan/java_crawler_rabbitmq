package org.example.models;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public record KeyValue(
        @SerializedName ("key") @NotNull String key,
        @SerializedName ("value") @NotNull String value
) {
}
