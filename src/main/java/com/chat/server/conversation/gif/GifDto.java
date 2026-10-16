package com.chat.server.conversation.gif;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Гифка для клиента: метаданные и URL для загрузки медиа. */
@Data
@Builder
public class GifDto {

    private UUID gifUuid;
    private String provider;
    private String providerGifId;
    private String title;
    private String url;
    private String previewUrl;
    private String mp4Url;
    private Integer width;
    private Integer height;
    private String rating;
    private String license;
    private String attribution;
    private String landingUrl;

    public static GifDto fromEntity(GifCatalog gif) {
        return GifDto.builder()
                .gifUuid(gif.getGifUuid())
                .provider(gif.getProvider())
                .providerGifId(gif.getProviderGifId())
                .title(gif.getTitle())
                .url(gif.deliverableUrl())
                .previewUrl(gif.getPreviewUrl())
                .mp4Url(gif.getMp4Url())
                .width(gif.getWidth())
                .height(gif.getHeight())
                .rating(gif.getRating())
                .license(gif.getLicense())
                .attribution(gif.getAttribution())
                .landingUrl(gif.getLandingUrl())
                .build();
    }
}
