package com.example.studentbot.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация MinIO клиента
 */
@Configuration
public class MinioConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(MinioConfig.class);
    
    @Value("${minio.url}")
    private String minioUrl;
    
    @Value("${minio.access-key}")
    private String accessKey;
    
    @Value("${minio.secret-key}")
    private String secretKey;
    
    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioUrl)
                    .credentials(accessKey, secretKey)
                    .build();
            
            logger.info("MinIO клиент успешно создан для URL: {}", minioUrl);
            return client;
            
        } catch (Exception e) {
            logger.error("Ошибка при создании MinIO клиента: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать MinIO клиента", e);
        }
    }
}