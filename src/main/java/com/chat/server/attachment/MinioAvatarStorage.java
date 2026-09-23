package com.chat.server.attachment;

import com.chat.server.exception.NotFoundException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class MinioAvatarStorage {
    private final MinioClient client;
    private final MinioConfig config;

    public void put(String key, byte[] image) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(config.getAvatarBucket()).build())) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(config.getAvatarBucket()).build());
                } catch (ErrorResponseException e) {
                    if (!"BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) throw e;
                }
            }
            client.putObject(PutObjectArgs.builder().bucket(config.getAvatarBucket()).object(key)
                    .stream(new ByteArrayInputStream(image), image.length, -1)
                    .contentType("image/png").build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload avatar to MinIO", e);
        }
    }

    public byte[] get(String key) {
        try (var stream = client.getObject(GetObjectArgs.builder()
                .bucket(config.getAvatarBucket()).object(key).build())) {
            return stream.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) throw new NotFoundException("Avatar not found");
            throw new IllegalStateException("Failed to load avatar from MinIO", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load avatar from MinIO", e);
        }
    }

    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(config.getAvatarBucket()).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete avatar from MinIO", e);
        }
    }
}
