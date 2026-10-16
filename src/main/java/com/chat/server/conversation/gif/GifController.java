package com.chat.server.conversation.gif;

import com.chat.server.conversation.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Гифки, использованные пользователем и в конкретном чате. Внешний поиск делает
 * клиент напрямую у провайдера; сервер хранит только те гифки, что были
 * отправлены (метаданные), и отдаёт списки «Мои»/«Этот чат».
 */
@RestController
@RequestMapping("/api/gifs")
@RequiredArgsConstructor
@Tag(name = "GIFs", description = "Каталог использованных гифок")
public class GifController {

    private static final int MAX_PAGE_SIZE = 60;

    private final GifCatalogPort gifCatalog;
    private final GifImportService gifImportService;
    private final ChatService chatService;

    @GetMapping("/recent")
    @Operation(summary = "Мои недавние гифки (по времени)")
    public ResponseEntity<List<GifDto>> recent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(gifCatalog.recent(userId, page, clamp(size)));
    }

    @GetMapping("/chat/{chatUuid}")
    @Operation(summary = "Гифки этого чата (по времени)")
    public ResponseEntity<List<GifDto>> chatGifs(
            @PathVariable UUID chatUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);
        chatService.validateUserAccessToChat(chatId, userId);
        return ResponseEntity.ok(gifCatalog.chatGifs(chatId, page, clamp(size)));
    }

    @GetMapping("/library")
    @Operation(summary = "Поиск по нашей библиотеке свободных гифок (CC0/PD)")
    public ResponseEntity<List<GifDto>> library(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        // Аутентификация нужна: эндпоинт доступен только нашим пользователям.
        Long.parseLong(authentication.getName());
        return ResponseEntity.ok(gifImportService.library(q, page, clamp(size)));
    }

    @GetMapping("/file/{gifUuid}")
    @Operation(summary = "Отдача зеркалированного файла гифки")
    public ResponseEntity<org.springframework.core.io.Resource> file(@PathVariable UUID gifUuid) {
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_GIF)
                .body(gifImportService.file(gifUuid));
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
