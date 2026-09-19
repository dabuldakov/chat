package com.chat.server.integration;

import com.chat.server.service.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadServiceIT extends AbstractIntegrationTest {

    private static final Path UPLOAD_DIR;

    static {
        try {
            UPLOAD_DIR = Files.createTempDirectory("chat-uploads");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> UPLOAD_DIR.toString());
    }

    @Autowired
    private FileUploadService fileUploadService;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
    }

    @Test
    void shouldStoreFile() {
        String storedPath = fileUploadService.store(file(), "attachments", "uuid_test.txt");

        assertThat(storedPath).isEqualTo("attachments/uuid_test.txt");
        assertThat(Files.exists(UPLOAD_DIR.resolve(storedPath))).isTrue();
    }

    @Test
    void shouldUploadAvatar() {
        String path = fileUploadService.uploadAvatar(42L, file());

        assertThat(path).startsWith("avatars/avatar_42_");
        assertThat(path).endsWith(".txt");
        assertThat(Files.exists(UPLOAD_DIR.resolve(path))).isTrue();
    }

    @Test
    void shouldUploadChatAvatar() {
        String path = fileUploadService.uploadChatAvatar(7L, file());

        assertThat(path).startsWith("chat_avatars/chat_avatar_7_");
        assertThat(Files.exists(UPLOAD_DIR.resolve(path))).isTrue();
    }

    @Test
    void shouldDeleteFile() {
        String storedPath = fileUploadService.store(file(), "attachments", "to-delete.txt");

        fileUploadService.delete(storedPath);

        assertThat(Files.exists(UPLOAD_DIR.resolve(storedPath))).isFalse();
    }
}