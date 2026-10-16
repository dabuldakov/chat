package com.chat.server.conversation.gif.openverse;

import com.chat.server.conversation.gif.GifSourceItem;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenverseGifSourceTest {

    private static final String JSON = """
            {
              "result_count": 1,
              "page_count": 1,
              "results": [
                {
                  "id": "abc-123",
                  "title": "cute cat",
                  "url": "https://cdn.example/cat.gif",
                  "thumbnail": "https://cdn.example/cat-thumb.gif",
                  "license": "cc0",
                  "license_version": "1.0",
                  "creator": "Jane",
                  "foreign_landing_url": "https://example/landing",
                  "filetype": "gif",
                  "attribution": "\\"cute cat\\" by Jane, CC0"
                }
              ]
            }
            """;

    @Test
    void mapsOpenverseResultsToSourceItems() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/images/")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        RestClient client = builder.baseUrl("https://api.openverse.org/v1")
                .defaultHeader("User-Agent", "test-agent").build();
        OpenverseGifSource source = new OpenverseGifSource(client);
        List<GifSourceItem> items = source.search("cat", 0, 3);

        assertThat(items).hasSize(1);
        GifSourceItem item = items.get(0);
        assertThat(item.source()).isEqualTo("openverse");
        assertThat(item.sourceGifId()).isEqualTo("abc-123");
        assertThat(item.url()).isEqualTo("https://cdn.example/cat.gif");
        assertThat(item.license()).isEqualTo("cc0");
        assertThat(item.creator()).isEqualTo("Jane");
        assertThat(item.attribution()).contains("CC0");
    }

    @Test
    void clampsPageSizeToAnonymousLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("page_size=20")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        RestClient client = builder.baseUrl("https://api.openverse.org/v1")
                .defaultHeader("User-Agent", "test-agent").build();
        OpenverseGifSource source = new OpenverseGifSource(client);

        source.search("cat", 0, 30);

        server.verify();
    }

    @Test
    void returnsEmptyOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/images/")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        RestClient client = builder.baseUrl("https://api.openverse.org/v1")
                .defaultHeader("User-Agent", "test-agent").build();
        OpenverseGifSource source = new OpenverseGifSource(client);

        assertThat(source.search("cat", 0, 3)).isEmpty();
    }
}
