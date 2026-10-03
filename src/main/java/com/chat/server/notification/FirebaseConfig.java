package com.chat.server.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

/**
 * Подключает Firebase Admin только когда включён флаг {@code fcm.enabled}
 * и задан файл сервисного аккаунта. Если Firebase не сконфигурирован,
 * конфигурация не активируется, а {@link FcmService} работает как no-op.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private final String serviceAccountFile;

    public FirebaseConfig(@Value("${fcm.service-account-file:firebase-service-account.json}") String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
    }

    @PostConstruct
    public void initialize() {
        try {
            ClassPathResource resource = new ClassPathResource(serviceAccountFile);
            if (!resource.exists()) {
                throw new IllegalStateException(
                        "Firebase service account file not found on classpath: " + serviceAccountFile);
            }

            if (FirebaseApp.getApps().isEmpty()) {
                try (InputStream serviceAccount = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
                    FirebaseApp.initializeApp(options);
                }
            }
            log.info("Firebase initialized from {}", serviceAccountFile);
        } catch (Exception e) {
            // fcm.enabled=true означает, что push обязателен: падаем сразу,
            // а не отдаём null-бин и не роняем контекст позже неочевидной ошибкой.
            throw new IllegalStateException("Failed to initialize Firebase", e);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}
