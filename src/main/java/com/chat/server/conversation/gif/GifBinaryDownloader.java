package com.chat.server.conversation.gif;

/**
 * Скачивание бинарника гифки из внешнего источника (для зеркалирования в MinIO).
 * Вынесено отдельным портом, чтобы импорт тестировался без сети.
 */
public interface GifBinaryDownloader {

    byte[] download(String url);
}
