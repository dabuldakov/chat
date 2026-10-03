package com.chat.server.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Удаление аккаунта вынесено из identity-модуля: это оркестрация очистки данных
 * всех модулей (identity, chat, messaging, storage), а не часть профиля.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Удаление аккаунта и персональных данных")
public class AccountController {

    private final AccountDeletionService accountDeletionService;

    @DeleteMapping("/me")
    @Operation(summary = "Удаление своего аккаунта вместе с персональными данными")
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        accountDeletionService.purgeAccount(Long.parseLong(authentication.getName()));
        return ResponseEntity.noContent().build();
    }
}
