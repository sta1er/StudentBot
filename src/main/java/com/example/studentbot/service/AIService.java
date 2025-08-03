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

/**
 * Сервис для работы с AI API (OpenRouter/Gemini)
 */
@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Value("${ai.api.provider:openrouter}")
    private String apiProvider;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${ai.openrouter.model:google/gemini-2.0-flash-exp:free}")
    private String openRouterModel;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${ai.max-tokens:2000}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final BookService bookService;

    public AIService(RestTemplate restTemplate, ObjectMapper objectMapper,
                     UserService userService, BookService bookService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.bookService = bookService;
    }

    /**
     * Обработка текстового сообщения пользователя
     */
    public String processTextMessage(String message, Long userId) {
        try {
            logger.info("Обработка сообщения от пользователя {}: {}", userId, message);

            // Получаем контекст пользователя
            String context = buildUserContext(userId, message);

            // Формируем системный промпт
            String systemPrompt = buildSystemPrompt();

            // Отправляем запрос к AI API
            String response = sendAIRequest(systemPrompt, message, context, null);

            logger.info("Получен ответ от AI API для пользователя {}", userId);
            return response;

        } catch (Exception e) {
            logger.error("Ошибка при обработке сообщения: {}", e.getMessage(), e);
            return "Извините, произошла ошибка при обработке вашего запроса. Попробуйте еще раз.";
        }
    }

    /**
     * Обработка вопроса по конкретной книге с файлом
     */
    public String processBookQuestion(String question, Long bookId, Long userId) {
        try {
            logger.info("Обработка вопроса по книге {} от пользователя {}", bookId, userId);

            // Получаем файл книги из MinIO
            Resource bookFile = bookService.getBookFile(bookId);
            String bookTitle = bookService.getBookTitle(bookId);

            String systemPrompt = String.format(
                    "Ты помощник для студентов. Отвечай на вопросы по загруженному документу '%s'. " +
                            "Будь конкретным и ссылайся на содержимое документа.", bookTitle);

            // Отправляем запрос с файлом
            return sendAIRequest(systemPrompt, question, null, bookFile);

        } catch (Exception e) {
            logger.error("Ошибка при обработке вопроса по книге: {}", e.getMessage(), e);
            return "К сожалению, не удалось найти ответ в указанной книге.";
        }
    }

    /**
     * Генерация краткого содержания книги
     */
    public String generateBookSummary(Long bookId, Long userId) {
        try {
            Resource bookFile = bookService.getBookFile(bookId);
            String bookTitle = bookService.getBookTitle(bookId);

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
    private String sendOpenRouterRequest(String systemPrompt, String userMessage, String context, Resource file)
            throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "https://localhost:8080");
        headers.set("X-Title", "Student Helper Bot");

        // Формируем сообщения
        List<Map<String, Object>> messages = new ArrayList<>();

        // Системное сообщение
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt + (context != null ? "\n\nКонтекст: " + context : ""));
        messages.add(systemMessage);

        // Пользовательское сообщение
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");

        if (file != null) {
            List<Map<String, Object>> contentList = new ArrayList<>();

            // Текстовая часть
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", userMessage);
            contentList.add(textContent);

            // Файл — кодируем в base64
            byte[] fileBytes = file.getInputStream().readAllBytes();
            String base64File = Base64.getEncoder().encodeToString(fileBytes);

            Map<String, Object> fileContent = new HashMap<>();
            fileContent.put("type", "file");
            // Например, для PDF:
            fileContent.put("file", "data:application/pdf;base64," + base64File);
            contentList.add(fileContent);

            // ВАЖНО: content для мультимодальных сообщений — это List
            userMsg.put("content", contentList);
        } else {
            userMsg.put("content", userMessage);
        }
        messages.add(userMsg);

        // Тело запроса
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
     * Построение системного промпта
     */
    private String buildSystemPrompt() {
        return "Ты - умный помощник для студентов. Отвечай на русском языке четко и полезно. " +
                "Если не знаешь ответа, честно скажи об этом. " +
                "При работе с документами ссылайся на их содержимое.";
    }

    /**
     * Построение контекста пользователя
     */
    private String buildUserContext(Long userId, String currentMessage) {
        StringBuilder context = new StringBuilder();
        try {
            // Добавляем информацию о пользователе
            var user = userService.getUserByTelegramId(userId);
            if (user.isPresent()) {
                context.append("Пользователь: ").append(user.get().getFirstName()).append("\n");
            }

            // Добавляем информацию о доступных книгах
            var userBooks = bookService.getUserBooks(userId);
            if (!userBooks.isEmpty()) {
                context.append("Доступные материалы:\n");
                userBooks.forEach(book ->
                        context.append("- ").append(book.getTitle()).append("\n")
                );
            }

        } catch (Exception e) {
            logger.warn("Не удалось построить контекст пользователя: {}", e.getMessage());
        }
        return context.toString();
    }

    /**
     * Проверка доступности AI API
     */
    public boolean isAIServiceAvailable() {
        try {
            // Простая проверка доступности API
            if ("openrouter".equals(apiProvider)) {
                String testUrl = openRouterBaseUrl + "/models";
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(apiKey);
                HttpEntity<?> entity = new HttpEntity<>(headers);

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