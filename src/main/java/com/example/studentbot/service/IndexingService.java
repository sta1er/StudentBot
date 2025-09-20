package com.example.studentbot.service;

import com.example.studentbot.model.BookMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис индексации с использованием Qdrant REST API
 */
@Service
public class IndexingService {
    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final AIService aiService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection.name}")
    private String collectionName;

    @Value("${qdrant.vector.size:1536}")
    private int vectorSize;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    // Оптимизированные размеры чанков
    private static final int CHUNK_SIZE = 200; // Уменьшено для лучшей векторизации
    private static final int CHUNK_OVERLAP = 50;
    private static final int MAX_CHUNK_LENGTH = 500; // Максимальная длина чанка в символах

    public IndexingService(AIService aiService, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(String.format("http://%s:%d", qdrantHost, qdrantPort))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @PostConstruct
    public void initializeCollection() {
        try {
            // Проверяем существование коллекции
            CompletableFuture<Boolean> collectionExists = checkCollectionExists();

            if (!collectionExists.get()) {
                // Коллекция не существует, создаем её
                logger.info("Создание коллекции {} с размерностью векторов {}", collectionName, vectorSize);
                createCollection().get();
                logger.info("Коллекция {} успешно создана", collectionName);
            } else {
                logger.info("Коллекция {} уже существует", collectionName);
            }
        } catch (Exception e) {
            logger.error("Ошибка при инициализации коллекции Qdrant: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать векторную базу данных", e);
        }
    }

    /**
     * Проверка существования коллекции через REST API
     */
    private CompletableFuture<Boolean> checkCollectionExists() {
        return webClient.get()
                .uri("/collections/{collection_name}", collectionName)
                .headers(this::addApiKeyHeader)
                .retrieve()
                .toEntity(String.class)
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorReturn(false)
                .toFuture();
    }

    /**
     * Создание коллекции через REST API
     */
    private CompletableFuture<Void> createCollection() {
        Map<String, Object> createRequest = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", "Cosine"
                )
        );

        return webClient.put()
                .uri("/collections/{collection_name}", collectionName)
                .headers(this::addApiKeyHeader)
                .bodyValue(createRequest)
                .retrieve()
                .toBodilessEntity()
                .then()
                .toFuture();
    }

    @Async
    public void processAndIndexBook(BookMetadata metadata, InputStream bookStream) {
        long startTime = System.currentTimeMillis();
        logger.info("Начало индексации книги ID: {} типа: {}", metadata.getId(), metadata.getFileType());

        try {
            String text = extractTextFromFile(bookStream, metadata.getFileType());

            if (text == null || text.trim().isEmpty()) {
                logger.warn("Не удалось извлечь текст из книги ID: {}", metadata.getId());
                return;
            }

            logger.info("Извлечено {} символов из книги ID: {}", text.length(), metadata.getId());

            List<String> chunks = splitTextIntoChunks(text);
            logger.info("Книга ID: {} разбита на {} чанков", metadata.getId(), chunks.size());

            List<Map<String, Object>> points = new ArrayList<>();
            int successfulChunks = 0;

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                try {
                    float[] vector = aiService.getEmbedding(chunk);
                    if (vector.length != vectorSize) {
                        logger.warn("Размерность вектора {} не соответствует ожидаемой {}", vector.length, vectorSize);
                        continue;
                    }

                    points.add(createPoint(vector, metadata, chunk, i));
                    successfulChunks++;
                } catch (Exception e) {
                    logger.warn("Не удалось получить вектор для чанка {} книги ID: {}. Ошибка: {}",
                            i, metadata.getId(), e.getMessage());
                }
            }

            if (!points.isEmpty()) {
                upsertPoints(points).get();
                logger.info("Успешно сохранено {} из {} векторов для книги ID: {}",
                        successfulChunks, chunks.size(), metadata.getId());
            } else {
                logger.error("Не удалось создать ни одного вектора для книги ID: {}", metadata.getId());
            }

        } catch (Exception e) {
            logger.error("Ошибка при индексации книги ID: {}: {}", metadata.getId(), e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Индексация книги ID: {} завершена за {} мс", metadata.getId(), duration);
        }
    }

    /**
     * Добавление точек в коллекцию через REST API
     */
    private CompletableFuture<Void> upsertPoints(List<Map<String, Object>> points) {
        Map<String, Object> upsertRequest = Map.of("points", points);

        return webClient.put()
                .uri("/collections/{collection_name}/points", collectionName)
                .headers(this::addApiKeyHeader)
                .bodyValue(upsertRequest)
                .retrieve()
                .toBodilessEntity()
                .then()
                .toFuture();
    }

    /**
     * Извлечение текста из файла в зависимости от его типа
     */
    private String extractTextFromFile(InputStream fileStream, String contentType) throws Exception {
        switch (contentType) {
            case "application/pdf":
                return extractTextFromPdf(fileStream);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return extractTextFromDocx(fileStream);
            case "text/plain":
                return extractTextFromTxt(fileStream);
            default:
                logger.warn("Неподдерживаемый тип файла: {}", contentType);
                return null;
        }
    }

    private String extractTextFromPdf(InputStream pdfStream) throws Exception {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream docxStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(docxStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractTextFromTxt(InputStream txtStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(txtStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Улучшенное разделение текста на чанки
     */
    private List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        // Сначала разбиваем по предложениям
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;

        for (String sentence : sentences) {
            // Если добавление предложения превысит лимит, сохраняем текущий чанк
            if (currentLength + sentence.length() > MAX_CHUNK_LENGTH && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());

                // Начинаем новый чанк с перекрытием (последние несколько предложений)
                String overlap = getLastSentences(currentChunk.toString(), CHUNK_OVERLAP);
                currentChunk = new StringBuilder(overlap);
                currentLength = overlap.length();
            }

            currentChunk.append(sentence).append(" ");
            currentLength += sentence.length() + 1;
        }

        // Добавляем последний чанк если он не пустой
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        // Если чанков слишком мало, используем старый метод разделения по словам
        if (chunks.size() < 2 && text.length() > MAX_CHUNK_LENGTH) {
            return splitTextByWords(text);
        }

        return chunks;
    }

    private String getLastSentences(String text, int overlapLength) {
        if (text.length() <= overlapLength) return text;

        String overlap = text.substring(Math.max(0, text.length() - overlapLength));
        int lastSentence = Math.max(
                Math.max(overlap.lastIndexOf('.'), overlap.lastIndexOf('!')),
                overlap.lastIndexOf('?')
        );

        if (lastSentence > 0) {
            return overlap.substring(lastSentence + 1).trim();
        }
        return overlap;
    }

    private List<String> splitTextByWords(String text) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE, words.length);
            List<String> chunkWords = new ArrayList<>();

            for (int i = start; i < end; i++) {
                chunkWords.add(words[i]);
            }

            chunks.add(String.join(" ", chunkWords));
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }

        return chunks;
    }

    private Map<String, Object> createPoint(float[] vector, BookMetadata metadata, String chunkText, int chunkIndex) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "vector", vector,
                "payload", Map.of(
                        "user_id", metadata.getUploadedBy().intValue(),
                        "book_id", metadata.getId().intValue(),
                        "book_title", metadata.getTitle(),
                        "text", chunkText,
                        "chunk_index", chunkIndex,
                        "chunk_length", chunkText.length()
                )
        );
    }

    /**
     * Добавление API ключа в заголовки если он настроен
     */
    private void addApiKeyHeader(HttpHeaders headers) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            headers.set("api-key", apiKey);
        }
    }
}