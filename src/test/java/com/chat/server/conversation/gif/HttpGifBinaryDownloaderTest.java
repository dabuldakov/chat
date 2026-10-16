package com.chat.server.conversation.gif;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpGifBinaryDownloaderTest {

    @Test
    void downloadsBytes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://cdn.example/cat.gif"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_GIF));

        HttpGifBinaryDownloader downloader = new HttpGifBinaryDownloader(
                builder.defaultHeader("User-Agent", "test-agent").build());

        assertThat(downloader.download("https://cdn.example/cat.gif")).containsExactly(1, 2, 3);
    }
}
