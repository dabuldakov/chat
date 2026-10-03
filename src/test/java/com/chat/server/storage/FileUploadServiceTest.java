package com.chat.server.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    private final MinioClient client = mock(MinioClient.class);

    private FileUploadService service;

    @BeforeEach
    void setUp() {
        MinioConfig config = new MinioConfig();
        config.setAttachmentBucket("attachments");
        config.setAvatarBucket("avatars");
        service = new FileUploadService(client, config);
    }

    private MultipartFile file() {
        return new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storeUploadsAndReturnsKeyWhenBucketExists() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        String key = service.store(file(), "attachments", "uuid_test.txt");

        assertThat(key).isEqualTo("attachments/uuid_test.txt");
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void storeCreatesBucketWhenMissing() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.store(file(), "attachments", "a.txt");

        verify(client).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void loadReturnsStreamedResource() throws Exception {
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(mock(GetObjectResponse.class));

        assertThat(service.load("attachments/a.txt")).isNotNull();
        verify(client).getObject(any(GetObjectArgs.class));
    }

    @Test
    void uploadAvatarAndChatAvatarUseDedicatedPrefixes() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        assertThat(service.uploadAvatar(42L, file())).startsWith("avatars/avatar_42_");
        assertThat(service.uploadChatAvatar(7L, file())).startsWith("chat_avatars/chat_avatar_7_");
    }

    @Test
    void deleteRemovesObject() throws Exception {
        service.delete("attachments/to-delete.txt");

        verify(client).removeObject(any(RemoveObjectArgs.class));
    }
}
