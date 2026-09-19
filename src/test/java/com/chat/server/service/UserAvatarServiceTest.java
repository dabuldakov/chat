package com.chat.server.service;

import com.chat.server.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAvatarServiceTest {
    private final MinioAvatarStorage storage = mock(MinioAvatarStorage.class);
    private final UserService users = mock(UserService.class);
    private final UserAvatarService service = new UserAvatarService(storage, users);

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "a.png", "image/png", output.toByteArray());
    }

    @Test
    void storageFailureDoesNotReplaceExistingAvatar() throws Exception {
        when(users.getUserById(1L)).thenReturn(User.builder().userUuid(UUID.randomUUID()).avatarUrl("/old").build());
        doThrow(new IllegalStateException("MinIO unavailable")).when(storage).put(anyString(), any());
        var file = image();
        assertThatThrownBy(() -> service.upload(1L, file)).isInstanceOf(IllegalStateException.class);
        verify(users, never()).updateAvatar(anyLong(), anyString());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void databaseFailureCleansNewObjectAndKeepsOldOne() throws Exception {
        String oldKey = UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
        when(users.getUserById(1L)).thenReturn(User.builder().userUuid(UUID.randomUUID())
                .avatarUrl("/api/avatars/" + oldKey).build());
        doThrow(new IllegalStateException("DB unavailable")).when(users).updateAvatar(eq(1L), anyString());
        var file = image();
        assertThatThrownBy(() -> service.upload(1L, file)).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(argThat(key -> !key.equals(oldKey)));
        verify(storage, never()).delete(oldKey);
    }
}
