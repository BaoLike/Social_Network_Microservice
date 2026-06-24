package com.notification.notification_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {
    @Value("${firebase.service-account:classpath:social-insta.json}")
    private Resource serviceAccountResource;

    @PostConstruct
    public void init() {
        try {
            if (!serviceAccountResource.exists()) {
                throw new IllegalStateException("Firebase service account file not found: " + serviceAccountResource);
            }

            try (InputStream serviceAccount = serviceAccountResource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase initialized successfully with {}", serviceAccountResource);
                } else {
                    log.info("Firebase app already initialized");
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Firebase from " + serviceAccountResource, e);
        }
    }
}