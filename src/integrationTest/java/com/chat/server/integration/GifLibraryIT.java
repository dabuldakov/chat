package com.chat.server.integration;

import com.chat.server.conversation.gif.GifBinaryDownloader;
import com.chat.server.conversation.gif.GifCatalogRepository;
import com.chat.server.conversation.gif.GifSourceItem;
import com.chat.server.conversation.gif.GifSourceProvider;
import com.chat.server.conversation.gif.GifSourceRegistry;
import com.chat.server.identity.JwtUtil;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Импорт свободных гифок: сервер ищет у источника (мок), зеркалит в хранилище
 * (in-memory) и отдаёт файл со своего API. Источник и загрузчик подменяем, чтобы
 * не ходить в сеть.
 */
@AutoConfigureMockMvc
class GifLibraryIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GifCatalogRepository gifCatalogRepository;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private GifSourceRegistry sources;
    @MockitoBean
    private GifBinaryDownloader downloader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GifSourceProvider provider = new GifSourceProvider() {
        @Override
        public String name() {
            return "openverse";
        }

        @Override
        public List<GifSourceItem> search(String query, int page, int size) {
            return List.of(new GifSourceItem("openverse", "ov-1", "cute cat",
                    "https://src/cat.gif", "https://src/cat-thumb.gif", 10, 20,
                    "cc0", "1.0", "Jane", "\"cat\" by Jane, CC0", "https://landing"));
        }
    };

    @BeforeEach
    void setUp() {
        when(sources.providers()).thenReturn(List.of(provider));
        when(downloader.download(any())).thenReturn(new byte[]{1, 2, 3, 4});
    }

    private String bearer(String username) {
        User user = userRepository.save(User.builder()
                .username(username).email(username + "@example.com").passwordHash("hash")
                .isOnline(false).isDeleted(false).build());
        return "Bearer " + jwtUtil.generateToken(user.getUserUuid(), user.getUsername());
    }

    @Test
    void libraryImportsMirrorsAndServesGif() throws Exception {
        String token = bearer("lib-user");

        String body = mvc.perform(get("/api/gifs/library").param("q", "cat")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].license").value("cc0"))
                .andExpect(jsonPath("$[0].url", startsWith("/api/gifs/file/")))
                .andReturn().getResponse().getContentAsString();

        String gifUuid = objectMapper.readTree(body).get(0).get("gifUuid").asText();

        // Файл отдаётся с нашего API (публично, по uuid).
        mvc.perform(get("/api/gifs/file/{uuid}", gifUuid))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_GIF));

        assertThat(gifCatalogRepository.findAll()).hasSize(1);
        var saved = gifCatalogRepository.findAll().get(0);
        assertThat(saved.getMirrored()).isTrue();
        assertThat(saved.getLicense()).isEqualTo("cc0");
        assertThat(saved.getAttribution()).isEqualTo("\"cat\" by Jane, CC0");
        assertThat(saved.getObjectKey()).isNotNull();
    }

    @Test
    void libraryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/gifs/library").param("q", "cat"))
                .andExpect(status().isUnauthorized());
    }
}
