package com.chat.server.controller;

import com.chat.server.service.UserAvatarService;
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

    @GetMapping(value = "/{userUuid}/{version}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable UUID userUuid, @PathVariable UUID version) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache()).body(avatars.get(userUuid, version));
    }
}
