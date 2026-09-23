package com.chat.server.attachment;

import com.chat.server.chat.ChatAvatarService;
import com.chat.server.user.UserAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/avatars")
@RequiredArgsConstructor
public class AvatarController {
    private final UserAvatarService avatars;
    private final ChatAvatarService chatAvatars;

    @GetMapping(value = "/{userUuid}/{version}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable UUID userUuid, @PathVariable UUID version) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache()).body(avatars.get(userUuid, version));
    }

    @GetMapping(value = "/chat/{chatUuid}/{version}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getChat(@PathVariable UUID chatUuid, @PathVariable UUID version) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache()).body(chatAvatars.get(chatUuid, version));
    }
}
