package com.example.studentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    public EmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Получение векторного представления для текста
     */
    public float[] getEmbedding(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Текст для векторизации не может быть пустым");
        }

        // Обрезаем текст если он слишком длинный для embedding модели
        String truncatedText = text.length() > 8000 ? text.substring(0, 8000) : text;

        String embeddingModel = "openai/text-embedding-3-small";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "Student Helper Bot");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", embeddingModel);
        requestBody.put("input", truncatedText);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = openRouterBaseUrl + "/embeddings";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = jsonResponse.get("data").get(0).get("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }

            logger.debug("Получен вектор размерности {} для текста длиной {} символов",
                    vector.length, truncatedText.length());

            return vector;
        } else {
            String errorMsg = "OpenRouter Embedding API error: " + response.getStatusCode();
            if (response.getBody() != null) {
                errorMsg += " - " + response.getBody();
            }
            throw new RuntimeException(errorMsg);
        }
    }
}