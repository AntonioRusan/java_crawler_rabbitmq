package org.example.models.message;

import com.google.gson.Gson;

public interface CrawlerMessage {
    Gson gson = new Gson();

    default String toJson() {
        return gson.toJson(this);
    }
}
