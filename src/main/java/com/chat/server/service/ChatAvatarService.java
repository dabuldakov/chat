package com.chat.server.service;

import com.chat.server.entity.Chat;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Аватары групповых чатов. Реализованы по образцу {@link UserAvatarService}:
 * изображение нормализуется в PNG, кладётся в MinIO под версионированным ключом,
 * а в БД пишется URL вида {@code /api/avatars/chat/{chatUuid}/{version}.png},
 * который затем отдаётся публичным GET-эндпоинтом.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAvatarService {
    private static final String PREFIX = "/api/avatars/";
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final MinioAvatarStorage storage;
    private final ChatService chats;

    public String upload(Long chatId, UUID chatUuid, Long userId, MultipartFile file) {
        Chat chat = chats.getChatById(chatId);
        if (!chat.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Only group creator can change the avatar");
        }
        byte[] image = normalize(file);
        String oldUrl = chat.getAvatarUrl();
        UUID version = UUID.randomUUID();
        String key = key(chatUuid, version);
        String url = PREFIX + key;
        storage.put(key, image);
        try {
            chats.updateAvatar(chatId, url);
        } catch (RuntimeException e) {
            deleteStored(url);
            throw e;
        }
        deleteStored(oldUrl);
        return url;
    }

    public byte[] get(UUID chatUuid, UUID version) {
        Long chatId = chats.getChatIdByUuid(chatUuid);
        Chat chat = chats.getChatById(chatId);
        String key = key(chatUuid, version);
        if (!(PREFIX + key).equals(chat.getAvatarUrl())) {
            throw new NotFoundException("Avatar not found");
        }
        return storage.get(key);
    }

    private String key(UUID chatUuid, UUID version) {
        return "chat/" + chatUuid + "/" + version + ".png";
    }

    private void deleteStored(String url) {
        if (url == null || !url.startsWith(PREFIX)) return;
        try {
            storage.delete(url.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            log.warn("Failed to clean up old chat avatar object", e);
        }
    }

    private byte[] normalize(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new BadRequestException("Avatar must be an image up to 5 MB");
        }
        try (var input = file.getInputStream(); var imageInput = ImageIO.createImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new BadRequestException("Unsupported avatar image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
                    throw new BadRequestException("Choose a JPEG or PNG avatar");
                }
                reader.setInput(imageInput);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192
                        || (long) width * height > 20_000_000) {
                    throw new BadRequestException("Avatar image dimensions are too large");
                }
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, 512.0 / Math.max(width, height));
                var resized = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, resized.getWidth(), resized.getHeight(), null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(resized, "png", output);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Cannot read avatar image");
        }
    }
}