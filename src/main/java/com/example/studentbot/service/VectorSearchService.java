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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис поиска векторов с использованием Qdrant REST API
 */
@Service
public class VectorSearchService {
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    private WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection.name}")
    private String collectionName;

    @Value("${qdrant.search.limit:8}")
    private int searchLimit;

    @Value("${qdrant.search.score_threshold:0.75}")
    private double scoreThreshold;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    public VectorSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeWebClient() {
        this.webClient = WebClient.builder()
                .baseUrl(String.format("http://%s:%d", qdrantHost, qdrantPort))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        logger.info("VectorSearchService WebClient инициализирован для Qdrant: http://{}:{}",
                qdrantHost, qdrantPort);
    }

    public List<String> findRelevantChunks(Long userId, float[] queryVector) {
        if (webClient == null) {
            logger.error("WebClient не инициализирован для поиска векторов");
            return Collections.emptyList();
        }

        if (queryVector == null || queryVector.length == 0) {
            logger.error("Вектор запроса пуст или null для пользователя {}", userId);
            return Collections.emptyList();
        }

        try {
            logger.debug("Поиск релевантных фрагментов для пользователя {} с вектором размерности {}",
                    userId, queryVector.length);

            Map<String, Object> searchRequest = createSearchRequest(queryVector, createUserFilter(userId));

            logger.debug("Запрос к Qdrant: {}", objectMapper.writeValueAsString(searchRequest));

            CompletableFuture<List<String>> searchFuture = performSearch(searchRequest);
            List<String> results = searchFuture.get();

            if (!results.isEmpty()) {
                logger.info("Найдено {} релевантных фрагментов для пользователя {}", results.size(), userId);
            } else {
                logger.debug("Не найдено релевантных фрагментов для пользователя {}", userId);
            }

            return results;

        } catch (Exception e) {
            logger.error("Ошибка при поиске для пользователя {}: {}", userId, e.getMessage(), e);

            if (e.getCause() instanceof WebClientResponseException) {
                WebClientResponseException webEx = (WebClientResponseException) e.getCause();
                logger.error("Детали ошибки Qdrant - Статус: {}, Тело ответа: {}",
                        webEx.getStatusCode(), webEx.getResponseBodyAsString());
            }

            return Collections.emptyList();
        }
    }

    /**
     * Поиск по конкретной книге
     */
    public List<String> findRelevantChunksInBook(Long userId, Long bookId, float[] queryVector) {
        if (queryVector == null || queryVector.length == 0) {
            logger.error("Вектор запроса пуст или null для пользователя {} и книги {}", userId, bookId);
            return Collections.emptyList();
        }

        try {
            logger.debug("Поиск в книге {} для пользователя {}", bookId, userId);

            Map<String, Object> searchRequest = createSearchRequest(queryVector, createBookFilter(userId, bookId));
            logger.debug("Запрос поиска в книге: {}", objectMapper.writeValueAsString(searchRequest));

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
                .doOnError(error -> {
                    logger.error("Ошибка при выполнении поиска в Qdrant: {}", error.getMessage());
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webError = (WebClientResponseException) error;
                        logger.error("HTTP статус: {}, Тело ответа: {}",
                                webError.getStatusCode(), webError.getResponseBodyAsString());
                    }
                })
                .onErrorReturn(Collections.emptyList())
                .toFuture();
    }

    /**
     * Создание запроса поиска
     */
    private Map<String, Object> createSearchRequest(float[] queryVector, Map<String, Object> filter) {
        Map<String, Object> request = new HashMap<>();

        List<Float> vectorList = new ArrayList<>();
        for (float f : queryVector) {
            vectorList.add(f);
        }

        request.put("vector", vectorList);
        request.put("limit", searchLimit);
        request.put("score_threshold", scoreThreshold);
        request.put("with_payload", true);

        if (filter != null && !filter.isEmpty()) {
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
            if (responseBody == null || responseBody.trim().isEmpty()) {
                logger.debug("Пустой ответ от Qdrant");
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.get("result");

            if (result == null || !result.isArray()) {
                logger.debug("Пустой результат поиска или неожиданная структура ответа");
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
            logger.error("Тело ответа: {}", responseBody);
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
     * Проверка доступности Qdrant
     */
    public boolean isQdrantAvailable() {
        try {
            String response = webClient.get()
                    .uri("/collections")
                    .headers(this::addApiKeyHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response != null;
        } catch (Exception e) {
            logger.warn("Qdrant недоступен: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка существования коллекции
     */
    public boolean isCollectionAvailable() {
        try {
            String response = webClient.get()
                    .uri("/collections/{collection_name}", collectionName)
                    .headers(this::addApiKeyHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response != null;
        } catch (Exception e) {
            logger.warn("Коллекция {} недоступна: {}", collectionName, e.getMessage());
            return false;
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