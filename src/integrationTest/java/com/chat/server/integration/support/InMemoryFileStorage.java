package com.chat.server.integration.support;

import com.chat.server.exception.NotFoundException;
import com.chat.server.storage.FileStorage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link FileStorage} для интеграционных тестов: проверяем
 * поведение приложения, а не S3. Убирает из CI зависимость от MinIO-контейнера.
 */
public class InMemoryFileStorage implements FileStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String store(MultipartFile file, String subdir, String fileName) {
        String key = subdir + "/" + fileName;
        try {
            objects.put(key, file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new NotFoundException("File not found");
        }
        return new ByteArrayResource(bytes);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        return store(file, "avatars", "avatar_" + userId + "_" + System.currentTimeMillis() + extension(file));
    }

    @Override
    public String uploadChatAvatar(Long chatId, MultipartFile file) {
        return store(file, "chat_avatars", "chat_avatar_" + chatId + "_" + System.currentTimeMillis() + extension(file));
    }

    @Override
    public void delete(String filePath) {
        objects.remove(filePath);
    }

    private String extension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }
}
