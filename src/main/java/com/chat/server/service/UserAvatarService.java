package com.chat.server.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService {
    private static final String PREFIX = "/api/avatars/";
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private final MinioAvatarStorage storage;
    private final UserService users;

    public String upload(Long userId, MultipartFile file) {
        byte[] image = normalize(file);
        var user = users.getUserById(userId);
        String oldUrl = user.getAvatarUrl();
        String key = user.getUserUuid() + "/" + UUID.randomUUID() + ".png";
        String url = PREFIX + key;
        storage.put(key, image);
        try {
            users.updateAvatar(userId, url);
        } catch (RuntimeException e) {
            deleteStored(url);
            throw e;
        }
        deleteStored(oldUrl);
        return url;
    }

    public byte[] get(UUID userUuid, UUID version) {
        String key = userUuid + "/" + version + ".png";
        if (!(PREFIX + key).equals(users.getUserByUuid(userUuid).getAvatarUrl())) {
            throw new NotFoundException("Avatar not found");
        }
        return storage.get(key);
    }

    public void delete(Long userId) {
        String oldUrl = users.getUserById(userId).getAvatarUrl();
        users.deleteAvatar(userId);
        deleteStored(oldUrl);
    }

    private void deleteStored(String url) {
        if (url == null || !url.startsWith(PREFIX)) return;
        try {
            storage.delete(url.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            log.warn("Failed to clean up old avatar object", e);
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
