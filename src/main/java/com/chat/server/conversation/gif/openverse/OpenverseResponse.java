package com.chat.server.conversation.gif.openverse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Ответ Openverse Images API (CC-лицензированные и public-domain изображения). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenverseResponse(
        @JsonProperty("result_count") Integer resultCount,
        @JsonProperty("page_count") Integer pageCount,
        List<OpenverseImage> results
) {
}
