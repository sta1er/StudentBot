package com.example.studentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.*;

@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Value("${ai.api.provider:openrouter}")
    private String apiProvider;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${ai.openrouter.model:deepseek/deepseek-r1-0528:free}")
    private String openRouterModel;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${ai.max-tokens:2000}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    @Value("${ai.rag.max-context-length:3000}")
    private int maxContextLength;

    @Value("${ai.rag.max-chunks:5}")
    private int maxChunks;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final VectorSearchService vectorSearchService;
    private final EmbeddingService embeddingService;

    public AIService(RestTemplate restTemplate, ObjectMapper objectMapper,
                     UserService userService, VectorSearchService vectorSearchService,
                     EmbeddingService embeddingService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.vectorSearchService = vectorSearchService;
        this.embeddingService = embeddingService;
    }

    /**
     * Улучшенная обработка текстового сообщения с RAG
     */
    public String processTextMessage(String message, Long userId) {
        try {
            logger.info("Обработка RAG-запроса от пользователя {}: {}", userId,
                    message.length() > 50 ? message.substring(0, 50) + "..." : message);

            float[] queryVector = embeddingService.getEmbedding(message);
            List<String> relevantChunks = vectorSearchService.findRelevantChunks(userId, queryVector);

            if (relevantChunks.isEmpty()) {
                logger.warn("Не найдено релевантных фрагментов для пользователя {}. Используем общие знания.", userId);
                String fallbackPrompt = "Ты - умный помощник для студентов. Отвечай на русском языке четко и полезно. " +
                        "В материалах пользователя не найдено информации по этому вопросу, поэтому отвечай, используя свои общие знания.";
                return sendAIRequest(fallbackPrompt, message, null, null);
            }

            // Оптимизируем контекст по длине
            String optimizedContext = optimizeContext(relevantChunks);
            String augmentedPrompt = buildAugmentedPrompt(optimizedContext, message);

            logger.debug("Используется контекст длиной {} символов из {} фрагментов",
                    optimizedContext.length(), relevantChunks.size());

            return sendAIRequest(augmentedPrompt, "", null, null);

        } catch (Exception e) {
            logger.error("Ошибка при обработке RAG-сообщения: {}", e.getMessage(), e);
            return "Извините, произошла ошибка при обработке вашего запроса. Попробуйте еще раз.";
        }
    }

    /**
     * Обработка вопроса по конкретной книге
     */
    public String processBookQuestion(String question, Long bookId, Long userId, String bookTitle, Resource bookFile) {
        try {
            logger.info("Обработка вопроса по книге {} от пользователя {}", bookId, userId);

            // Сначала пробуем найти ответ в векторной базе
            float[] queryVector = embeddingService.getEmbedding(question);
            List<String> relevantChunks = vectorSearchService.findRelevantChunksInBook(userId, bookId, queryVector);

            if (!relevantChunks.isEmpty()) {
                String context = optimizeContext(relevantChunks);
                String augmentedPrompt = buildBookQuestionPrompt(context, question, bookTitle);
                return sendAIRequest(augmentedPrompt, "", null, null);
            }

            // Если в векторной базе нет данных, пробуем работать с файлом напрямую
            logger.debug("Векторные данные не найдены, пробуем работать с файлом напрямую");

            String systemPrompt = String.format(
                    "Ты помощник для студентов. Отвечай на вопросы по загруженному документу '%s'. " +
                            "Будь конкретным и ссылайся на содержимое документа.", bookTitle);

            return sendAIRequest(systemPrompt, question, null, bookFile);

        } catch (Exception e) {
            logger.error("Ошибка при обработке вопроса по книге: {}", e.getMessage(), e);
            return "К сожалению, не удалось найти ответ в указанной книге.";
        }
    }

    /**
     * Генерация краткого содержания книги
     */
    public String generateBookSummary(Long bookId, Long userId, String bookTitle, Resource bookFile) {
        try {
            // Пробуем собрать краткое содержание из векторных данных
            String summaryQuery = "основные темы ключевые идеи выводы содержание";
            float[] queryVector = embeddingService.getEmbedding(summaryQuery);
            List<String> relevantChunks = vectorSearchService.findRelevantChunksInBook(userId, bookId, queryVector);

            if (!relevantChunks.isEmpty()) {
                String context = optimizeContext(relevantChunks);
                String prompt = buildSummaryPrompt(context, bookTitle);
                return sendAIRequest(prompt, "", null, null);
            }

            // Если векторных данных нет, работаем с файлом
            String systemPrompt = "Создай структурированное краткое содержание загруженного документа. " +
                    "Выдели основные темы, ключевые идеи и выводы.";
            String userPrompt = String.format("Создай краткое содержание документа '%s'", bookTitle);

            return sendAIRequest(systemPrompt, userPrompt, null, bookFile);

        } catch (Exception e) {
            logger.error("Ошибка при генерации краткого содержания: {}", e.getMessage(), e);
            return "Не удалось создать краткое содержание книги.";
        }
    }

    /**
     * Оптимизация контекста по длине для избежания превышения лимитов модели
     */
    private String optimizeContext(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int chunksUsed = 0;

        for (String chunk : chunks) {
            if (chunksUsed >= maxChunks) {
                break;
            }

            // Проверяем, не превысим ли лимит контекста
            if (context.length() + chunk.length() + 50 > maxContextLength) { // +50 для разделителей
                // Если это первый чанк и он слишком большой, обрезаем его
                if (chunksUsed == 0) {
                    String truncatedChunk = chunk.substring(0, Math.min(chunk.length(), maxContextLength - 100));
                    context.append(truncatedChunk);
                    if (truncatedChunk.length() < chunk.length()) {
                        context.append("...[обрезано]");
                    }
                    chunksUsed++;
                }
                break;
            }

            if (chunksUsed > 0) {
                context.append("\n\n---\n\n");
            }
            context.append(chunk);
            chunksUsed++;
        }

        logger.debug("Оптимизирован контекст: использовано {} из {} фрагментов, итоговая длина {} символов",
                chunksUsed, chunks.size(), context.length());

        return context.toString();
    }

    /**
     * Построение промпта для RAG
     */
    private String buildAugmentedPrompt(String context, String userQuestion) {
        return "Ты — экспертный ассистент для студентов. Твоя задача — ответить на вопрос пользователя, " +
                "основываясь на предоставленном контексте из загруженных пользователем материалов.\n\n" +

                "ПРАВИЛА:\n" +
                "1. Отвечай ТОЛЬКО на основе предоставленного контекста\n" +
                "2. Если ответа нет в контексте, честно скажи: 'В предоставленных материалах нет информации для ответа на этот вопрос'\n" +
                "3. Цитируй конкретные фрагменты из контекста\n" +
                "4. Отвечай четко, структурированно и по делу\n" +
                "5. Используй русский язык\n\n" +

                "=== КОНТЕКСТ ИЗ МАТЕРИАЛОВ ПОЛЬЗОВАТЕЛЯ ===\n" +
                context + "\n" +
                "=== КОНЕЦ КОНТЕКСТА ===\n\n" +

                "ВОПРОС: " + userQuestion + "\n\n" +
                "ОТВЕТ:";
    }

    private String buildBookQuestionPrompt(String context, String question, String bookTitle) {
        return String.format(
                "Ты отвечаешь на вопрос по книге '%s'. Используй только информацию из предоставленного контекста.\n\n" +
                        "КОНТЕКСТ:\n%s\n\n" +
                        "ВОПРОС: %s\n\n" +
                        "Дай точный ответ, основанный только на контексте:",
                bookTitle, context, question);
    }

    private String buildSummaryPrompt(String context, String bookTitle) {
        return String.format(
                "На основе следующих фрагментов из книги '%s', создай структурированное краткое содержание:\n\n" +
                        "ФРАГМЕНТЫ:\n%s\n\n" +
                        "Создай краткое содержание, включающее:\n" +
                        "1. Основные темы\n" +
                        "2. Ключевые идеи\n" +
                        "3. Важные выводы\n" +
                        "4. Структуру материала",
                bookTitle, context);
    }

    /**
     * Отправка запроса к AI API
     */
    private String sendAIRequest(String systemPrompt, String userMessage, String context, Resource file)
            throws Exception {
        if ("openrouter".equals(apiProvider)) {
            return sendOpenRouterRequest(systemPrompt, userMessage, context, file);
        } else if ("gemini".equals(apiProvider)) {
            return sendGeminiRequest(systemPrompt, userMessage, context, file);
        } else {
            throw new IllegalStateException("Неподдерживаемый провайдер AI: " + apiProvider);
        }
    }

    /**
     * Отправка запроса к OpenRouter API
     */
    private String sendOpenRouterRequest(String systemPrompt, String userMessage, String context, Resource file) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "Student Helper Bot");

        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // Если userMessage не пустой, добавляем его
        if (userMessage != null && !userMessage.isEmpty()) {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openRouterModel);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                openRouterBaseUrl + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("choices").get(0).get("message").get("content").asText();
        } else {
            throw new RuntimeException("OpenRouter API error: " + response.getStatusCode());
        }
    }

    /**
     * Отправка запроса к Gemini API
     */
    private String sendGeminiRequest(String systemPrompt, String userMessage, String context, Resource file)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Формируем контент
        List<Map<String, Object>> parts = new ArrayList<>();

        // Текстовая часть
        Map<String, Object> textPart = new HashMap<>();
        String fullPrompt = systemPrompt + "\n\n" + userMessage;
        if (context != null) {
            fullPrompt += "\n\nКонтекст: " + context;
        }

        textPart.put("text", fullPrompt);
        parts.add(textPart);

        // Если есть файл, добавляем его
        if (file != null) {
            try {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String base64File = Base64.getEncoder().encodeToString(fileBytes);

                Map<String, Object> filePart = new HashMap<>();
                Map<String, Object> inlineData = new HashMap<>();
                inlineData.put("mime_type", "application/pdf"); // Определяем MIME-type
                inlineData.put("data", base64File);
                filePart.put("inline_data", inlineData);
                parts.add(filePart);
            } catch (IOException e) {
                logger.warn("Не удалось прочитать файл для Gemini API: {}", e.getMessage());
            }
        }

        // Тело запроса
        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        // Конфигурация генерации
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens);
        generationConfig.put("temperature", temperature);
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = String.format("%s/models/%s:generateContent?key=%s",
                geminiBaseUrl, geminiModel, apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("candidates").get(0)
                    .get("content").get("parts").get(0)
                    .get("text").asText();
        } else {
            throw new RuntimeException("Gemini API error: " + response.getStatusCode());
        }
    }

    /**
     * Проверка доступности AI API
     */
    public boolean isAIServiceAvailable() {
        try {
            if ("openrouter".equals(apiProvider)) {
                String testUrl = openRouterBaseUrl + "/models";
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(apiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        testUrl, HttpMethod.GET, entity, String.class);
                return response.getStatusCode().is2xxSuccessful();
            }
            return true; // Для Gemini пока просто возвращаем true
        } catch (Exception e) {
            logger.warn("AI API недоступен: {}", e.getMessage());
            return false;
        }
    }
}