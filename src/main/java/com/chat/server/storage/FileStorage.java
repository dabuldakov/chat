package com.chat.server.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Порт хранилища файлов вложений/аватаров. Реализация — MinIO
 * ({@link FileUploadService}); в интеграционных тестах подменяется in-memory
 * реализацией, чтобы не тянуть внешний контейнер.
 */
public interface FileStorage {

    String store(MultipartFile file, String subdir, String fileName);

    Resource load(String key);

    String uploadAvatar(Long userId, MultipartFile file);

    String uploadChatAvatar(Long chatId, MultipartFile file);

    void delete(String filePath);
}
