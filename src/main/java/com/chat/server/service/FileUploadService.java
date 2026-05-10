package com.chat.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class FileUploadService {

    @Value("${file.upload-dir:/var/chat-app/uploads}")
    private String uploadDir;

    public String store(MultipartFile file, String subdir, String fileName) {
        try {
            Path targetLocation = Paths.get(uploadDir, subdir).toAbsolutePath().normalize();
            Files.createDirectories(targetLocation);

            Path targetFile = targetLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

            return subdir + "/" + fileName;
        } catch (IOException e) {
            log.error("Failed to store file: {}", fileName, e);
            throw new RuntimeException("Failed to store file", e);
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
            Path path = Paths.get(uploadDir, filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", filePath, e);
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