package com.chat.server.conversation.gif;

import com.chat.server.exception.NotFoundException;
import com.chat.server.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifImportServiceTest {

    @Mock
    private GifSourceRegistry sources;
    @Mock
    private GifBinaryDownloader downloader;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private GifCatalogRepository catalogRepository;

    @InjectMocks
    private GifImportService service;

    private final GifSourceProvider provider = new GifSourceProvider() {
        @Override
        public String name() {
            return "openverse";
        }

        @Override
        public List<GifSourceItem> search(String query, int page, int size) {
            return List.of(new GifSourceItem("openverse", "id-1", "cute cat",
                    "https://src/cat.gif", "https://src/cat-thumb.gif", 100, 80,
                    "cc0", "1.0", "author", "\"cat\" by author, CC0", "https://landing"));
        }
    };

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxMirrorBytes", 10_000_000L);
        lenient().when(sources.providers()).thenReturn(List.of(provider));
        lenient().when(catalogRepository.save(any())).thenAnswer(inv -> {
            GifCatalog gif = inv.getArgument(0);
            if (gif.getGifUuid() == null) {
                gif.setGifUuid(UUID.randomUUID());
            }
            return gif;
        });
    }

    @Test
    void importsAndMirrorsGifWithLicenseAndTags() {
        when(catalogRepository.findByProviderAndProviderGifId("openverse", "id-1"))
                .thenReturn(Optional.empty());
        when(downloader.download("https://src/cat.gif")).thenReturn(new byte[]{1, 2, 3, 4});
        when(fileStorage.storeBytes(any(), eq("image/gif"), eq("gifs"), any()))
                .thenReturn("gifs/openverse_id-1.gif");

        List<GifDto> result = service.library("cat", 0, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).startsWith("/api/gifs/file/");
        assertThat(result.get(0).getLicense()).isEqualTo("cc0");
        assertThat(result.get(0).getAttribution()).contains("CC0");

        ArgumentCaptor<GifCatalog> captor = ArgumentCaptor.forClass(GifCatalog.class);
        org.mockito.Mockito.verify(catalogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        GifCatalog saved = captor.getValue();
        assertThat(saved.getMirrored()).isTrue();
        assertThat(saved.getObjectKey()).isEqualTo("gifs/openverse_id-1.gif");
        assertThat(saved.getTags()).contains("cat");
    }

    @Test
    void reusesAlreadyMirroredGifWithoutDownload() {
        GifCatalog existing = GifCatalog.builder()
                .gifId(5L).gifUuid(UUID.randomUUID()).provider("openverse").providerGifId("id-1")
                .publicUrl("/api/gifs/file/known").mirrored(true).objectKey("gifs/x.gif").build();
        when(catalogRepository.findByProviderAndProviderGifId("openverse", "id-1"))
                .thenReturn(Optional.of(existing));

        List<GifDto> result = service.library("cat", 0, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("/api/gifs/file/known");
        org.mockito.Mockito.verifyNoInteractions(downloader);
    }

    @Test
    void skipsWhenDownloadFails() {
        when(catalogRepository.findByProviderAndProviderGifId("openverse", "id-1"))
                .thenReturn(Optional.empty());
        when(downloader.download("https://src/cat.gif")).thenThrow(new RuntimeException("boom"));

        assertThat(service.library("cat", 0, 30)).isEmpty();
    }

    @Test
    void skipsOversizedFile() {
        ReflectionTestUtils.setField(service, "maxMirrorBytes", 2L);
        when(catalogRepository.findByProviderAndProviderGifId("openverse", "id-1"))
                .thenReturn(Optional.empty());
        when(downloader.download("https://src/cat.gif")).thenReturn(new byte[]{1, 2, 3, 4});

        assertThat(service.library("cat", 0, 30)).isEmpty();
    }

    @Test
    void fileThrowsWhenNotMirrored() {
        UUID uuid = UUID.randomUUID();
        when(catalogRepository.findByGifUuid(uuid)).thenReturn(Optional.of(
                GifCatalog.builder().gifUuid(uuid).provider("klipy").providerGifId("s").mirrored(false).build()));

        assertThatThrownBy(() -> service.file(uuid)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void fileReturnsStoredResource() {
        UUID uuid = UUID.randomUUID();
        when(catalogRepository.findByGifUuid(uuid)).thenReturn(Optional.of(
                GifCatalog.builder().gifUuid(uuid).provider("openverse").providerGifId("s")
                        .mirrored(true).objectKey("gifs/a.gif").build()));
        when(fileStorage.load("gifs/a.gif")).thenReturn(new ByteArrayResource(new byte[]{9}));

        assertThat(service.file(uuid)).isNotNull();
    }
}
