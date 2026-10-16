package com.chat.server.conversation.gif;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GifSourceRegistryTest {

    private static final class FakeProvider implements GifSourceProvider {
        @Override
        public String name() {
            return "openverse";
        }

        @Override
        public List<GifSourceItem> search(String query, int page, int size) {
            return List.of(new GifSourceItem(
                    "openverse", "id-1", "cat", "url", "thumb", 10, 20,
                    "cc0", "1.0", "author", "\"cat\" by author, CC0", "landing"));
        }
    }

    @Test
    void exposesRegisteredProviders() {
        GifSourceRegistry registry = new GifSourceRegistry(List.of(new FakeProvider()));

        assertThat(registry.providers()).hasSize(1);
        assertThat(registry.byName("openverse")).isPresent();
        assertThat(registry.byName("missing")).isEmpty();
    }

    @Test
    void providerReturnsRedistributableItem() {
        GifSourceItem item = new FakeProvider().search("cat", 0, 10).get(0);

        assertThat(item.source()).isEqualTo("openverse");
        assertThat(item.license()).isEqualTo("cc0");
        assertThat(item.attribution()).contains("CC0");
    }

    @Test
    void emptyRegistryIsAllowed() {
        GifSourceRegistry registry = new GifSourceRegistry(List.of());

        assertThat(registry.providers()).isEmpty();
    }
}
