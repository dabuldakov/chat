package com.chat.server.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fcm.service-account-file:firebase-service-account.json}")
    private String serviceAccountFile;

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Firebase with file: {}", serviceAccountFile);

            // Пробуем загрузить файл
            ClassPathResource resource = new ClassPathResource(serviceAccountFile);

            if (!resource.exists()) {
                log.error("Firebase service account file not found: {}", serviceAccountFile);
                log.error("Current classpath: {}", System.getProperty("java.class.path"));
                return;
            }

            log.info("File found, size: {} bytes", resource.contentLength());

            InputStream serviceAccount = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase application initialized successfully");
            } else {
                log.info("Firebase application already initialized");
            }

        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage(), e);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.warn("FirebaseApp not initialized, trying to initialize...");
                initialize();
            }

            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseMessaging.getInstance();
            }
        } catch (Exception e) {
            log.error("Failed to get FirebaseMessaging instance: {}", e.getMessage());
        }

        log.warn("Returning null FirebaseMessaging bean");
        return null;
    }
}