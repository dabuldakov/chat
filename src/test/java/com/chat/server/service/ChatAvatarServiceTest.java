package com.chat.server.service;

import com.chat.server.entity.Chat;
import com.chat.server.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatAvatarServiceTest {
    private final MinioAvatarStorage storage = mock(MinioAvatarStorage.class);
    private final ChatService chats = mock(ChatService.class);
    private final ChatAvatarService service = new ChatAvatarService(storage, chats);

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "a.png", "image/png", output.toByteArray());
    }

    @Test
    void uploadStoresNormalizedPngAndUpdatesChat() throws Exception {
        UUID chatUuid = UUID.randomUUID();
        when(chats.getChatById(1L)).thenReturn(Chat.builder().chatUuid(chatUuid).avatarUrl(null).build());

        String url = service.upload(1L, chatUuid, image());

        assertThat(url).startsWith("/api/avatars/chat/" + chatUuid + "/").endsWith(".png");
        verify(storage).put(argThat(key -> key.startsWith("chat/" + chatUuid + "/")), any());
        verify(chats).updateAvatar(eq(1L), eq(url));
    }

    @Test
    void storageFailureDoesNotReplaceExistingAvatar() throws Exception {
        UUID chatUuid = UUID.randomUUID();
        when(chats.getChatById(1L)).thenReturn(Chat.builder().chatUuid(chatUuid).avatarUrl("/old").build());
        doThrow(new IllegalStateException("MinIO unavailable")).when(storage).put(anyString(), any());

        var file = image();
        assertThatThrownBy(() -> service.upload(1L, chatUuid, file)).isInstanceOf(IllegalStateException.class);
        verify(chats, never()).updateAvatar(anyLong(), anyString());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void databaseFailureCleansNewObjectAndKeepsOldOne() throws Exception {
        UUID chatUuid = UUID.randomUUID();
        String oldKey = "chat/" + chatUuid + "/" + UUID.randomUUID() + ".png";
        when(chats.getChatById(1L)).thenReturn(Chat.builder().chatUuid(chatUuid)
                .avatarUrl("/api/avatars/" + oldKey).build());
        doThrow(new IllegalStateException("DB unavailable")).when(chats).updateAvatar(eq(1L), anyString());

        var file = image();
        assertThatThrownBy(() -> service.upload(1L, chatUuid, file)).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(argThat(key -> !key.equals(oldKey)));
        verify(storage, never()).delete(oldKey);
    }

    @Test
    void getRejectsVersionThatIsNotCurrentAvatar() {
        UUID chatUuid = UUID.randomUUID();
        when(chats.getChatIdByUuid(chatUuid)).thenReturn(1L);
        when(chats.getChatById(1L)).thenReturn(Chat.builder().chatUuid(chatUuid)
                .avatarUrl("/api/avatars/chat/" + chatUuid + "/" + UUID.randomUUID() + ".png").build());

        assertThatThrownBy(() -> service.get(chatUuid, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        verify(storage, never()).get(anyString());
    }
}