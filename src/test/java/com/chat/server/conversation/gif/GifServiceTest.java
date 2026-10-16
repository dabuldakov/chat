package com.chat.server.conversation.gif;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifServiceTest {

    @Mock
    private GifCatalogRepository catalogRepository;
    @Mock
    private GifUserUsageRepository userUsageRepository;
    @Mock
    private GifChatUsageRepository chatUsageRepository;

    @InjectMocks
    private GifService gifService;

    private GifPayload payload() {
        GifPayload p = new GifPayload();
        p.setProvider("klipy");
        p.setProviderGifId("slug-1");
        p.setTitle("cat");
        p.setUrl("https://cdn.klipy.com/cat.gif");
        p.setPreviewUrl("https://cdn.klipy.com/cat-tiny.gif");
        p.setWidth(200);
        p.setHeight(150);
        p.setRating("g");
        return p;
    }

    @Test
    void upsertCreatesNewCatalogEntry() {
        when(catalogRepository.findByProviderAndProviderGifId("klipy", "slug-1"))
                .thenReturn(Optional.empty());
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GifCatalog saved = gifService.upsert(payload());

        assertThat(saved.getProvider()).isEqualTo("klipy");
        assertThat(saved.getProviderGifId()).isEqualTo("slug-1");
        assertThat(saved.getSourceUrl()).isEqualTo("https://cdn.klipy.com/cat.gif");
        assertThat(saved.getWidth()).isEqualTo(200);
    }

    @Test
    void upsertReusesExistingCatalogEntry() {
        GifCatalog existing = GifCatalog.builder()
                .gifId(10L).provider("klipy").providerGifId("slug-1").useCount(5L).build();
        when(catalogRepository.findByProviderAndProviderGifId("klipy", "slug-1"))
                .thenReturn(Optional.of(existing));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GifCatalog saved = gifService.upsert(payload());

        assertThat(saved.getGifId()).isEqualTo(10L);
        assertThat(saved.getUseCount()).isEqualTo(5L);
    }

    @Test
    void recordSentBumpsCatalogAndBothUsages() {
        GifCatalog existing = GifCatalog.builder()
                .gifId(7L).provider("klipy").providerGifId("slug-1").useCount(2L).build();
        when(catalogRepository.findByProviderAndProviderGifId("klipy", "slug-1"))
                .thenReturn(Optional.of(existing));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userUsageRepository.findByUserIdAndGifId(1L, 7L)).thenReturn(Optional.empty());
        when(chatUsageRepository.findByChatIdAndGifId(2L, 7L)).thenReturn(Optional.empty());
        when(userUsageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatUsageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        gifService.recordSent(payload(), 1L, 2L);

        assertThat(existing.getUseCount()).isEqualTo(3L);
        assertThat(existing.getLastUsedAt()).isNotNull();

        ArgumentCaptor<GifUserUsage> userCaptor = ArgumentCaptor.forClass(GifUserUsage.class);
        verify(userUsageRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUseCount()).isEqualTo(1L);

        ArgumentCaptor<GifChatUsage> chatCaptor = ArgumentCaptor.forClass(GifChatUsage.class);
        verify(chatUsageRepository).save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getChatId()).isEqualTo(2L);
    }

    @Test
    void recentReturnsGifsInUsageOrder() {
        when(userUsageRepository.findByUserIdOrderByLastUsedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(
                        GifUserUsage.builder().gifId(30L).build(),
                        GifUserUsage.builder().gifId(20L).build()));
        when(catalogRepository.findByGifIdIn(any())).thenReturn(List.of(
                GifCatalog.builder().gifId(20L).provider("klipy").providerGifId("b")
                        .sourceUrl("u20").build(),
                GifCatalog.builder().gifId(30L).provider("klipy").providerGifId("c")
                        .sourceUrl("u30").build()));

        List<GifDto> dtos = gifService.recent(1L, 0, 30);

        assertThat(dtos).extracting(GifDto::getProviderGifId).containsExactly("c", "b");
    }
}
