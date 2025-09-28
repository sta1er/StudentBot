package com.example.studentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Провайдер embedding (gemini, openai, huggingface)
    @Value("${embedding.provider:gemini}")
    private String embeddingProvider;

    // Gemini Embedding настройки
    @Value("${embedding.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${embedding.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${embedding.gemini.model:models/text-embedding-004}")
    private String geminiModel;

    @Value("${embedding.gemini.task-type:RETRIEVAL_DOCUMENT}")
    private String geminiTaskType;

    // OpenAI Embedding настройки (альтернатива)
    @Value("${embedding.openai.api-key:}")
    private String openaiApiKey;

    @Value("${embedding.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    // HuggingFace Embedding настройки (бесплатная альтернатива)
    @Value("${embedding.huggingface.api-key:}")
    private String huggingfaceApiKey;

    @Value("${embedding.huggingface.base-url:https://api-inference.huggingface.co}")
    private String huggingfaceBaseUrl;

    @Value("${embedding.huggingface.model:sentence-transformers/all-MiniLM-L6-v2}")
    private String huggingfaceModel;

    // Yandex Embedding настройки
    @Value("${embedding.yandex.api-key:}")
    private String yandexApiKey;

    @Value("${embedding.yandex.base-url:https://llm.api.cloud.yandex.net}")
    private String yandexBaseUrl;

    @Value("${embedding.yandex.folder-id:}")
    private String yandexFolderId;

    @Value("${embedding.yandex.model:text-search-doc}")
    private String yandexModel; // text-search-doc или text-search-query

    public EmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Получение векторного представления для текста
     * Автоматически выбирает провайдера на основе конфигурации
     */
    public float[] getEmbedding(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Текст для векторизации не может быть пустым");
        }

        // Обрезаем текст если он слишком длинный
        String truncatedText = text.length() > 8000 ? text.substring(0, 8000) : text;

        logger.debug("Получение embedding через провайдера: {} для текста длиной {} символов",
                embeddingProvider, truncatedText.length());

        switch (embeddingProvider.toLowerCase()) {
            case "gemini":
                return getGeminiEmbedding(truncatedText);
            case "openai":
                return getOpenAIEmbedding(truncatedText);
            case "huggingface":
                return getHuggingFaceEmbedding(truncatedText);
            case "yandex":
                return getYandexEmbedding(truncatedText);
            default:
                throw new IllegalArgumentException("Неподдерживаемый провайдер embedding: " + embeddingProvider);
        }
    }

    /**
     * Получение embedding через Google Gemini API
     */
    private float[] getGeminiEmbedding(String text) throws Exception {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            throw new RuntimeException("Gemini API ключ не настроен. Задайте GEMINI_EMBEDDING_API_KEY");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", geminiModel);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(Map.of("text", text)));
        requestBody.put("content", content);

        // Добавляем task_type для лучшего качества embedding
        requestBody.put("task_type", geminiTaskType);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = String.format("%s/%s:embedContent?key=%s",
                geminiBaseUrl, geminiModel, geminiApiKey);

        logger.debug("Отправка запроса к Gemini API: {}", url.replaceAll("key=.*", "key=***"));

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = jsonResponse.get("embedding").get("values");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Неожиданный формат ответа от Gemini API");
            }

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }

            logger.debug("Получен Gemini вектор размерности {} для текста длиной {} символов",
                    vector.length, text.length());

            return vector;
        } else {
            String errorMsg = "Gemini Embedding API error: " + response.getStatusCode();
            if (response.getBody() != null) {
                errorMsg += " - " + response.getBody();
            }
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Получение embedding через OpenAI API (альтернативный метод)
     */
    private float[] getOpenAIEmbedding(String text) throws Exception {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            throw new RuntimeException("OpenAI API ключ не настроен. Задайте OPENAI_EMBEDDING_API_KEY");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("input", text);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = openaiBaseUrl + "/embeddings";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = jsonResponse.get("data").get(0).get("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }

            logger.debug("Получен OpenAI вектор размерности {} для текста длиной {} символов",
                    vector.length, text.length());

            return vector;
        } else {
            String errorMsg = "OpenAI Embedding API error: " + response.getStatusCode();
            if (response.getBody() != null) {
                errorMsg += " - " + response.getBody();
            }
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Получение embedding через HuggingFace API (бесплатная альтернатива)
     */
    private float[] getHuggingFaceEmbedding(String text) throws Exception {
        if (huggingfaceApiKey == null || huggingfaceApiKey.trim().isEmpty()) {
            throw new RuntimeException("HuggingFace API ключ не настроен. Задайте HUGGINGFACE_API_KEY");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(huggingfaceApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", text);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = huggingfaceBaseUrl + "/models/" + huggingfaceModel;

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            // HuggingFace возвращает массив векторов или один вектор
            JsonNode vectorNode;
            if (jsonResponse.isArray()) {
                vectorNode = jsonResponse.get(0);
            } else {
                vectorNode = jsonResponse;
            }

            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = vectorNode.get(i).floatValue();
            }

            logger.debug("Получен HuggingFace вектор размерности {} для текста длиной {} символов",
                    vector.length, text.length());

            return vector;
        } else {
            String errorMsg = "HuggingFace API error: " + response.getStatusCode();
            if (response.getBody() != null) {
                errorMsg += " - " + response.getBody();
            }
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Получение embedding через Yandex Cloud Foundation Models API
     */
    private float[] getYandexEmbedding(String text) throws Exception {
        if (yandexApiKey == null || yandexApiKey.trim().isEmpty()) {
            throw new RuntimeException("Yandex API ключ не настроен. Задайте YANDEX_EMBEDDING_API_KEY");
        }

        if (yandexFolderId == null || yandexFolderId.trim().isEmpty()) {
            throw new RuntimeException("Yandex Folder ID не настроен. Задайте YANDEX_FOLDER_ID");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Api-Key " + yandexApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelUri", String.format("emb://%s/%s/latest", yandexFolderId, yandexModel));
        requestBody.put("text", text);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = yandexBaseUrl + "/foundationModels/v1/textEmbedding";

        logger.debug("Отправка запроса к Yandex API: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = jsonResponse.get("embedding");

                if (embeddingNode == null || !embeddingNode.isArray()) {
                    throw new RuntimeException("Неожиданный формат ответа от Yandex API");
                }

                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).floatValue();
                }

                logger.debug("Получен Yandex вектор размерности {} для текста длиной {} символов",
                        vector.length, text.length());
                return vector;
            } else {
                String errorMsg = "Yandex Embedding API error: " + response.getStatusCode();
                if (response.getBody() != null) {
                    errorMsg += " - " + response.getBody();
                }
                throw new RuntimeException(errorMsg);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обращении к Yandex API: {}", e.getMessage());
            throw e;
        }
    }


    /**
     * Проверка доступности embedding сервиса
     */
    public boolean isEmbeddingServiceAvailable() {
        try {
            // Тестируем с коротким текстом
            float[] testVector = getEmbedding("test");
            return testVector != null && testVector.length > 0;
        } catch (Exception e) {
            logger.warn("Embedding сервис недоступен: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получение информации о размерности векторов текущего провайдера
     */
    public int getVectorDimensions() {
        switch (embeddingProvider.toLowerCase()) {
            case "gemini":
                // text-embedding-004: 768 dimensions
                // text-embedding-001: 768 dimensions
                return 768;
            case "openai":
                // text-embedding-3-small: 1536 dimensions
                // text-embedding-ada-002: 1536 dimensions
                return 1536;
            case "huggingface":
                // all-MiniLM-L6-v2: 384 dimensions
                // Другие модели могут иметь разные размерности
                return 384;
            case "yandex":
                return 256;
            default:
                return 768; // По умолчанию для Gemini
        }
    }
}