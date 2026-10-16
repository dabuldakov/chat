package com.chat.server.conversation.gif;

import org.springframework.web.client.RestClient;

/** Скачивание гифки по URL через HTTP (RestClient). */
public class HttpGifBinaryDownloader implements GifBinaryDownloader {

    private final RestClient client;

    public HttpGifBinaryDownloader(RestClient client) {
        this.client = client;
    }

    @Override
    public byte[] download(String url) {
        return client.get().uri(url).retrieve().body(byte[].class);
    }
}
