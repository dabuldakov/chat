package com.chat.server.conversation;

import com.chat.server.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentPolicyTest {

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Test
    void jpegAndPngAreAllowed() {
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("photo.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})));
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("photo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})));

        assertEquals(Attachment.AttachmentType.IMAGE,
                AttachmentPolicy.validate(file("photo.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).type());
    }

    @Test
    void pdfAndOfficeAreAllowed() {
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("doc.pdf", "%PDF-1.7".getBytes())));
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("doc.docx", new byte[]{0x50, 0x4B, 0x03, 0x04})));
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("doc.doc", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})));

        assertEquals(Attachment.AttachmentType.DOCUMENT,
                AttachmentPolicy.validate(file("doc.pdf", "%PDF".getBytes())).type());
    }

    @Test
    void apkIsAllowedAsDocument() {
        assertEquals(Attachment.AttachmentType.DOCUMENT,
                AttachmentPolicy.validate(
                        file("muzea-1.7.28-beta.apk", new byte[]{0x50, 0x4B, 0x03, 0x04})).type());
        assertThrows(BadRequestException.class,
                () -> AttachmentPolicy.validate(file("fake.apk", new byte[]{'M', 'Z'})));
    }

    @Test
    void audioAndVideoAreAllowed() {
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("clip.mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'})));
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("song.mp3", new byte[]{'I', 'D', '3', 4})));
        assertDoesNotThrow(() -> AttachmentPolicy.validate(
                file("voice.ogg", new byte[]{'O', 'g', 'g', 'S'})));

        assertEquals(Attachment.AttachmentType.AUDIO,
                AttachmentPolicy.validate(file("song.mp3", new byte[]{'I', 'D', '3'})).type());
    }

    @Test
    void executableAndHtmlAreRejected() {
        assertThrows(BadRequestException.class,
                () -> AttachmentPolicy.validate(file("virus.exe", new byte[]{'M', 'Z'})));
        assertThrows(BadRequestException.class,
                () -> AttachmentPolicy.validate(file("page.html", "<html>".getBytes())));
        assertThrows(BadRequestException.class,
                () -> AttachmentPolicy.validate(file("archive.zip", new byte[]{0x50, 0x4B, 0x03, 0x04})));
    }

    @Test
    void contentMustMatchExtension() {
        // PNG, переименованный в jpg
        assertThrows(BadRequestException.class, () -> AttachmentPolicy.validate(
                file("fake.jpg", new byte[]{(byte) 0x89, 'P', 'N', 'G'})));
        // exe, переименованный в pdf
        assertThrows(BadRequestException.class, () -> AttachmentPolicy.validate(
                file("fake.pdf", new byte[]{'M', 'Z'})));
    }

    @Test
    void sizeLimitIsEnforced() {
        byte[] tooLarge = new byte[(int) AttachmentPolicy.MAX_IMAGE_BYTES + 1];
        tooLarge[0] = (byte) 0xFF;
        tooLarge[1] = (byte) 0xD8;
        tooLarge[2] = (byte) 0xFF;

        assertThrows(BadRequestException.class,
                () -> AttachmentPolicy.validate(file("big.jpg", tooLarge)));
    }

    @Test
    void textFilesRejectBinaryContent() {
        assertDoesNotThrow(() -> AttachmentPolicy.validate(file("notes.txt", "hello".getBytes())));
        assertThrows(BadRequestException.class, () -> AttachmentPolicy.validate(
                file("notes.txt", new byte[]{'a', 0, 'b'})));
    }

    @Test
    void thumbnailMustBeAnImageAndSmall() {
        assertDoesNotThrow(() -> AttachmentPolicy.validateThumbnail(
                file("thumb.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})));
        assertThrows(BadRequestException.class, () -> AttachmentPolicy.validateThumbnail(
                file("thumb.mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'})));
    }
}
