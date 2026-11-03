package com.chat.server.conversation;

import com.chat.server.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Белый список типов вложений чата.
 *
 * <p>Проверяем не только расширение и Content-Type (их клиент может подделать),
 * но и сигнатуру файла. Так в чат не попадёт исполняемый файл, переименованный
 * в .jpg, и не откроется XSS через HTML/SVG.
 */
public final class AttachmentPolicy {

    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
    public static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    public static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;
    public static final long MAX_DOCUMENT_BYTES = 100L * 1024 * 1024;
    public static final long MAX_THUMBNAIL_BYTES = 5L * 1024 * 1024;

    private static final int SIGNATURE_BYTES = 16;

    private static final Map<String, Rule> RULES = Map.ofEntries(
            entry("jpg", Attachment.AttachmentType.IMAGE, MAX_IMAGE_BYTES, "image/jpeg", AttachmentPolicy::jpeg),
            entry("jpeg", Attachment.AttachmentType.IMAGE, MAX_IMAGE_BYTES, "image/jpeg", AttachmentPolicy::jpeg),
            entry("png", Attachment.AttachmentType.IMAGE, MAX_IMAGE_BYTES, "image/png", AttachmentPolicy::png),
            entry("webp", Attachment.AttachmentType.IMAGE, MAX_IMAGE_BYTES, "image/webp", AttachmentPolicy::webp),

            entry("mp4", Attachment.AttachmentType.VIDEO, MAX_VIDEO_BYTES, "video/mp4", AttachmentPolicy::ftyp),
            entry("mov", Attachment.AttachmentType.VIDEO, MAX_VIDEO_BYTES, "video/quicktime", AttachmentPolicy::ftyp),

            entry("mp3", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/mpeg", AttachmentPolicy::mp3),
            entry("m4a", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/mp4", AttachmentPolicy::ftyp),
            entry("aac", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/aac", AttachmentPolicy::aac),
            entry("ogg", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/ogg", AttachmentPolicy::ogg),
            entry("opus", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/ogg", AttachmentPolicy::ogg),
            entry("wav", Attachment.AttachmentType.AUDIO, MAX_AUDIO_BYTES, "audio/wav", AttachmentPolicy::wav),

            entry("pdf", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "application/pdf", AttachmentPolicy::pdf),
            entry("txt", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "text/plain", AttachmentPolicy::text),
            entry("csv", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "text/csv", AttachmentPolicy::text),
            entry("doc", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "application/msword", AttachmentPolicy::ole),
            entry("docx", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", AttachmentPolicy::zip),
            entry("xls", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "application/vnd.ms-excel", AttachmentPolicy::ole),
            entry("xlsx", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", AttachmentPolicy::zip),
            entry("ppt", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES, "application/vnd.ms-powerpoint", AttachmentPolicy::ole),
            entry("pptx", Attachment.AttachmentType.DOCUMENT, MAX_DOCUMENT_BYTES,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", AttachmentPolicy::zip)
    );

    private AttachmentPolicy() {
    }

    /** Проверяет файл и возвращает его тип; при нарушении — 400. */
    public static Rule validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String originalName = file.getOriginalFilename();
        String extension = extension(originalName);
        Rule rule = RULES.get(extension);
        if (rule == null) {
            throw new BadRequestException("File type is not allowed: " + extension);
        }
        if (file.getSize() > rule.maxBytes) {
            throw new BadRequestException(
                    "File is too large, maximum " + (rule.maxBytes / (1024 * 1024)) + " MB");
        }
        byte[] head = readHead(file);
        if (!rule.signature.matches(head)) {
            throw new BadRequestException("File content does not match its extension");
        }
        return rule;
    }

    /** Превью — только изображения, небольшой размер. */
    public static void validateThumbnail(MultipartFile file) {
        Rule rule = validate(file);
        if (rule.type != Attachment.AttachmentType.IMAGE) {
            throw new BadRequestException("Thumbnail must be an image");
        }
        if (file.getSize() > MAX_THUMBNAIL_BYTES) {
            throw new BadRequestException("Thumbnail is too large");
        }
    }

    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(SIGNATURE_BYTES);
        } catch (IOException e) {
            throw new BadRequestException("Cannot read the file");
        }
    }

    private static boolean jpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean png(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean webp(byte[] b) {
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static boolean ftyp(byte[] b) {
        return b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    private static boolean ogg(byte[] b) {
        return b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
    }

    private static boolean wav(byte[] b) {
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    private static boolean mp3(byte[] b) {
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return b.length >= 2 && (b[0] & 0xFF) == 0xFF && ((b[1] & 0xE0) == 0xE0);
    }

    private static boolean aac(byte[] b) {
        return b.length >= 2 && (b[0] & 0xFF) == 0xFF && ((b[1] & 0xF6) == 0xF0);
    }

    private static boolean pdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    private static boolean zip(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0x50 && (b[1] & 0xFF) == 0x4B
                && (b[2] & 0xFF) == 0x03 && (b[3] & 0xFF) == 0x04;
    }

    private static boolean ole(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF
                && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0;
    }

    private static boolean text(byte[] b) {
        // Текстовые файлы не имеют сигнатуры: достаточно, чтобы не было NUL.
        for (byte value : b) {
            if (value == 0) return false;
        }
        return true;
    }

    private static Map.Entry<String, Rule> entry(
            String extension,
            Attachment.AttachmentType type,
            long maxBytes,
            String mimeType,
            Signature signature
    ) {
        return Map.entry(extension, new Rule(type, maxBytes, mimeType, signature));
    }

    public record Rule(
            Attachment.AttachmentType type,
            long maxBytes,
            String mimeType,
            Signature signature
    ) {
    }

    @FunctionalInterface
    public interface Signature {
        boolean matches(byte[] head);
    }
}
