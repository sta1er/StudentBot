package com.example.studentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис поиска векторов с использованием Qdrant REST API
 */
@Service
public class VectorSearchService {
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection.name}")
    private String collectionName;

    @Value("${qdrant.search.limit:5}")
    private int searchLimit;

    @Value("${qdrant.search.score_threshold:0.7}")
    private double scoreThreshold;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    public VectorSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(String.format("http://%s:%d", qdrantHost, qdrantPort))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Поиск релевантных фрагментов текста для пользователя
     */
    public List<String> findRelevantChunks(Long userId, float[] queryVector) {
        try {
            logger.debug("Поиск релевантных фрагментов для пользователя {} с вектором размерности {}",
                    userId, queryVector.length);

            Map<String, Object> searchRequest = createSearchRequest(queryVector, createUserFilter(userId));

            CompletableFuture<List<String>> searchFuture = performSearch(searchRequest);
            List<String> results = searchFuture.get();

            if (!results.isEmpty()) {
                logger.info("Найдено {} релевантных фрагментов для пользователя {}", results.size(), userId);
            }

            return results;

        } catch (Exception e) {
            logger.error("Ошибка при поиске для пользователя {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Поиск по конкретной книге
     */
    public List<String> findRelevantChunksInBook(Long userId, Long bookId, float[] queryVector) {
        try {
            logger.debug("Поиск в книге {} для пользователя {}", bookId, userId);

            Map<String, Object> searchRequest = createSearchRequest(queryVector, createBookFilter(userId, bookId));

            CompletableFuture<List<String>> searchFuture = performSearch(searchRequest);
            return searchFuture.get();

        } catch (Exception e) {
            logger.error("Ошибка при поиске в книге {} для пользователя {}: {}", bookId, userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Выполнение поиска через REST API
     */
    private CompletableFuture<List<String>> performSearch(Map<String, Object> searchRequest) {
        return webClient.post()
                .uri("/collections/{collection_name}/points/search", collectionName)
                .headers(this::addApiKeyHeader)
                .bodyValue(searchRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSearchResponse)
                .toFuture();
    }

    /**
     * Создание запроса поиска
     */
    private Map<String, Object> createSearchRequest(float[] queryVector, Map<String, Object> filter) {
        Map<String, Object> request = new HashMap<>();
        request.put("vector", queryVector);
        request.put("limit", searchLimit);
        request.put("score_threshold", scoreThreshold);
        request.put("with_payload", true);

        if (filter != null) {
            request.put("filter", filter);
        }

        return request;
    }

    /**
     * Создание фильтра по пользователю
     */
    private Map<String, Object> createUserFilter(Long userId) {
        return Map.of(
                "must", List.of(
                        Map.of(
                                "key", "user_id",
                                "match", Map.of("value", userId.intValue())
                        )
                )
        );
    }

    /**
     * Создание фильтра по пользователю и книге
     */
    private Map<String, Object> createBookFilter(Long userId, Long bookId) {
        return Map.of(
                "must", List.of(
                        Map.of(
                                "key", "user_id",
                                "match", Map.of("value", userId.intValue())
                        ),
                        Map.of(
                                "key", "book_id",
                                "match", Map.of("value", bookId.intValue())
                        )
                )
        );
    }

    /**
     * Парсинг ответа поиска
     */
    private List<String> parseSearchResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.get("result");

            if (result == null || !result.isArray()) {
                logger.debug("Пустой результат поиска");
                return Collections.emptyList();
            }

            List<String> texts = new ArrayList<>();

            for (JsonNode item : result) {
                JsonNode payload = item.get("payload");
                if (payload != null) {
                    JsonNode textNode = payload.get("text");
                    if (textNode != null && textNode.isTextual()) {
                        String text = textNode.asText();
                        if (!text.trim().isEmpty()) {
                            texts.add(text);
                        }
                    }
                }
            }

            return texts;

        } catch (Exception e) {
            logger.error("Ошибка при парсинге ответа поиска: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получение статистики по векторным данным пользователя
     */
    public VectorStats getUserVectorStats(Long userId) {
        try {
            // Используем scroll API для получения всех записей пользователя
            Map<String, Object> scrollRequest = Map.of(
                    "filter", createUserFilter(userId),
                    "limit", 10000,
                    "with_payload", true
            );

            String responseBody = webClient.post()
                    .uri("/collections/{collection_name}/points/scroll", collectionName)
                    .headers(this::addApiKeyHeader)
                    .bodyValue(scrollRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseStatsResponse(responseBody);

        } catch (Exception e) {
            logger.warn("Не удалось получить статистику векторов для пользователя {}: {}", userId, e.getMessage());
            return new VectorStats(0, 0);
        }
    }

    /**
     * Парсинг ответа статистики
     */
    private VectorStats parseStatsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.get("result");

            if (result == null) {
                return new VectorStats(0, 0);
            }

            JsonNode points = result.get("points");
            if (points == null || !points.isArray()) {
                return new VectorStats(0, 0);
            }

            Set<Integer> uniqueBooks = new HashSet<>();
            int totalVectors = 0;

            for (JsonNode point : points) {
                totalVectors++;
                JsonNode payload = point.get("payload");
                if (payload != null) {
                    JsonNode bookIdNode = payload.get("book_id");
                    if (bookIdNode != null && bookIdNode.isInt()) {
                        uniqueBooks.add(bookIdNode.asInt());
                    }
                }
            }

            return new VectorStats(totalVectors, uniqueBooks.size());

        } catch (Exception e) {
            logger.error("Ошибка при парсинге статистики: {}", e.getMessage(), e);
            return new VectorStats(0, 0);
        }
    }

    /**
     * Добавление API ключа в заголовки если он настроен
     */
    private void addApiKeyHeader(HttpHeaders headers) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            headers.set("api-key", apiKey);
        }
    }

    /**
     * Статистика векторных данных
     */
    public static class VectorStats {
        private final long totalVectors;
        private final long uniqueBooks;

        public VectorStats(long totalVectors, long uniqueBooks) {
            this.totalVectors = totalVectors;
            this.uniqueBooks = uniqueBooks;
        }

        public long getTotalVectors() { return totalVectors; }
        public long getUniqueBooks() { return uniqueBooks; }
    }
}