package com.chat.server.conversation.gif;

import com.chat.server.conversation.gif.openverse.OpenverseGifSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиенты для источников гифок. Собираем RestClient сами (а не через
 * автоконфиг), чтобы провайдеры и загрузчик были тестируемы через
 * MockRestServiceServer.
 */
@Configuration
public class GifHttpConfig {

    @Bean
    @ConditionalOnProperty(name = "app.gifs.openverse.enabled", havingValue = "true", matchIfMissing = true)
    public OpenverseGifSource openverseGifSource(
            @Value("${app.gifs.openverse.base-url:https://api.openverse.org/v1}") String baseUrl,
            @Value("${app.gifs.openverse.user-agent:muzea/1.0 (+https://muzea.su)}") String userAgent) {
        return new OpenverseGifSource(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build());
    }

    @Bean
    public GifBinaryDownloader gifBinaryDownloader(
            @Value("${app.gifs.openverse.user-agent:muzea/1.0 (+https://muzea.su)}") String userAgent) {
        return new HttpGifBinaryDownloader(RestClient.builder()
                .defaultHeader("User-Agent", userAgent)
                .build());
    }
}
