package com.chat.server.service;

import com.chat.server.config.MinioConfig;
import com.chat.server.exception.NotFoundException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Хранилище загружаемых файлов в MinIO. Раньше вложения писались на локальный
 * диск контейнера и терялись при перезапуске — теперь и вложения, и аватары
 * чатов лежат в объектном хранилище.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public String store(MultipartFile file, String subdir, String fileName) {
        String key = subdir + "/" + fileName;
        try (InputStream input = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getAttachmentBucket())
                    .object(key)
                    .stream(input, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Stored file in MinIO: {}", key);
            return key;
        } catch (Exception e) {
            log.error("Failed to store file: {}", key, e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public Resource load(String key) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getAttachmentBucket())
                    .object(key)
                    .build());
            return new InputStreamResource(stream);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new NotFoundException("File not found");
            }
            throw new RuntimeException("Failed to load file", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file", e);
        }
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = "avatar_" + userId + "_" + System.currentTimeMillis() + extension;
        return store(file, "avatars", fileName);
    }

    public String uploadChatAvatar(Long chatId, MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = "chat_avatar_" + chatId + "_" + System.currentTimeMillis() + extension;
        return store(file, "chat_avatars", fileName);
    }

    public void delete(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getAttachmentBucket())
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete file: {}", filePath, e);
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = minioConfig.getAttachmentBucket();
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            } catch (ErrorResponseException e) {
                if (!"BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) {
                    throw e;
                }
            }
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }
}