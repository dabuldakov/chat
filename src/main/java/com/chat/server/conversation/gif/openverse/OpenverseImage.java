package com.chat.server.conversation.gif.openverse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Одна картинка в ответе Openverse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenverseImage(
        String id,
        String title,
        String url,
        String thumbnail,
        String license,
        @JsonProperty("license_version") String licenseVersion,
        String creator,
        @JsonProperty("foreign_landing_url") String foreignLandingUrl,
        String filetype,
        String attribution
) {
}
