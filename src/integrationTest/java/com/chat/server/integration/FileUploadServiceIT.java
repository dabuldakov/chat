package com.chat.server.integration;

import com.chat.server.exception.NotFoundException;
import com.chat.server.attachment.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FileUploadService fileUploadService;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldStoreAndLoadFile() throws Exception {
        String storedPath = fileUploadService.store(file(), "attachments", "uuid_test.txt");

        assertThat(storedPath).isEqualTo("attachments/uuid_test.txt");
        assertThat(fileUploadService.load(storedPath).getContentAsByteArray())
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldUploadAvatar() {
        String path = fileUploadService.uploadAvatar(42L, file());

        assertThat(path).startsWith("avatars/avatar_42_");
        assertThat(path).endsWith(".txt");
        assertThat(fileUploadService.load(path).exists()).isTrue();
    }

    @Test
    void shouldUploadChatAvatar() {
        String path = fileUploadService.uploadChatAvatar(7L, file());

        assertThat(path).startsWith("chat_avatars/chat_avatar_7_");
        assertThat(fileUploadService.load(path).exists()).isTrue();
    }

    @Test
    void shouldDeleteFile() {
        String storedPath = fileUploadService.store(file(), "attachments", "to-delete.txt");

        fileUploadService.delete(storedPath);

        assertThatThrownBy(() -> fileUploadService.load(storedPath))
                .isInstanceOf(NotFoundException.class);
    }
}