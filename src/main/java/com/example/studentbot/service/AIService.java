package com.example.studentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с локальным ИИ
 */
@Service
public class AIService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    
    @Value("${ai.service.url}")
    private String aiServiceUrl;
    
    @Value("${ai.service.timeout:30000}")
    private int timeout;
    
    @Value("${ai.service.max-tokens:2000}")
    private int maxTokens;
    
    @Value("${ai.service.temperature:0.7}")
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
            
            // Формируем запрос к AI сервису
            Map<String, Object> request = createAIRequest(message, context);
            
            // Отправляем запрос
            String response = sendAIRequest(request);
            
            logger.info("Получен ответ от AI сервиса для пользователя {}", userId);
            return response;
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке сообщения: {}", e.getMessage(), e);
            return "Извините, произошла ошибка при обработке вашего запроса. Попробуйте еще раз или переформулируйте вопрос.";
        }
    }
    
    /**
     * Обработка вопроса по конкретной книге
     */
    public String processBookQuestion(String question, Long bookId, Long userId) {
        try {
            logger.info("Обработка вопроса по книге {} от пользователя {}", bookId, userId);
            
            // Получаем содержимое книги
            String bookContent = bookService.getBookContent(bookId);
            
            // Формируем контекст с содержимым книги
            String context = String.format(
                "Книга: %s\n\nСодержимое:\n%s\n\nВопрос пользователя: %s",
                bookService.getBookTitle(bookId),
                bookContent,
                question
            );
            
            Map<String, Object> request = createAIRequest(question, context);
            request.put("book_id", bookId.toString());
            
            return sendAIRequest(request);
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке вопроса по книге: {}", e.getMessage(), e);
            return "К сожалению, не удалось найти ответ в указанной книге. Проверьте, загружена ли она корректно.";
        }
    }
    
    /**
     * Генерация краткого содержания книги
     */
    public String generateBookSummary(Long bookId, Long userId) {
        try {
            String bookContent = bookService.getBookContent(bookId);
            String bookTitle = bookService.getBookTitle(bookId);
            
            String prompt = String.format(
                "Создай краткое содержание книги '%s'. Выдели основные темы, ключевые идеи и выводы. " +
                "Структурируй ответ с использованием заголовков и списков для лучшей читаемости.",
                bookTitle
            );
            
            Map<String, Object> request = createAIRequest(prompt, bookContent);
            request.put("task_type", "summarization");
            
            return sendAIRequest(request);
            
        } catch (Exception e) {
            logger.error("Ошибка при генерации краткого содержания: {}", e.getMessage(), e);
            return "Не удалось создать краткое содержание книги.";
        }
    }
    
    /**
     * Объяснение сложных концепций
     */
    public String explainConcept(String concept, String context, Long userId) {
        try {
            String prompt = String.format(
                "Объясни концепцию '%s' простыми словами. Используй примеры и аналогии для лучшего понимания. " +
                "Если есть связанные темы, упомяни их. Ответ должен быть доступным для студентов.",
                concept
            );
            
            Map<String, Object> request = createAIRequest(prompt, context);
            request.put("task_type", "explanation");
            
            return sendAIRequest(request);
            
        } catch (Exception e) {
            logger.error("Ошибка при объяснении концепции: {}", e.getMessage(), e);
            return "Не удалось объяснить данную концепцию. Попробуйте переформулировать запрос.";
        }
    }
    
    /**
     * Помощь с домашним заданием
     */
    public String helpWithHomework(String homework, Long userId) {
        try {
            String prompt = String.format(
                "Помоги с домашним заданием: '%s'. " +
                "Не давай готовые ответы, а направляй студента к правильному решению. " +
                "Задавай наводящие вопросы и объясняй подходы к решению.",
                homework
            );
            
            // Получаем контекст из книг пользователя
            String userBooksContext = buildUserBooksContext(userId);
            
            Map<String, Object> request = createAIRequest(prompt, userBooksContext);
            request.put("task_type", "homework_help");
            
            return sendAIRequest(request);
            
        } catch (Exception e) {
            logger.error("Ошибка при помощи с домашним заданием: {}", e.getMessage(), e);
            return "Не удалось помочь с домашним заданием. Попробуйте задать более конкретный вопрос.";
        }
    }
    
    /**
     * Создание запроса к AI сервису
     */
    private Map<String, Object> createAIRequest(String message, String context) {
        Map<String, Object> request = new HashMap<>();
        request.put("message", message);
        request.put("context", context);
        request.put("max_tokens", maxTokens);
        request.put("temperature", temperature);
        request.put("timestamp", System.currentTimeMillis());
        
        return request;
    }
    
    /**
     * Отправка запроса к AI сервису
     */
    private String sendAIRequest(Map<String, Object> request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        String url = aiServiceUrl + "/api/chat";
        
        ResponseEntity<String> response = restTemplate.exchange(
            url, 
            HttpMethod.POST, 
            entity, 
            String.class
        );
        
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("response").asText();
        } else {
            throw new RuntimeException("AI service returned error: " + response.getStatusCode());
        }
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
                context.append("Язык: ").append(user.get().getLanguageCode()).append("\n");
            }
            
            // Добавляем последние сообщения для контекста беседы
            // (можно реализовать получение последних N сообщений)
            
            // Добавляем информацию о доступных книгах
            var userBooks = bookService.getUserBooks(userId);
            if (!userBooks.isEmpty()) {
                context.append("\nДоступные материалы:\n");
                userBooks.forEach(book -> {
                    context.append("- ").append(book.getTitle());
                    if (book.getAuthor() != null) {
                        context.append(" (").append(book.getAuthor()).append(")");
                    }
                    context.append("\n");
                });
            }
            
        } catch (Exception e) {
            logger.warn("Не удалось построить полный контекст пользователя: {}", e.getMessage());
        }
        
        return context.toString();
    }
    
    /**
     * Построение контекста из книг пользователя
     */
    private String buildUserBooksContext(Long userId) {
        StringBuilder context = new StringBuilder();
        
        try {
            var userBooks = bookService.getUserBooks(userId);
            for (var book : userBooks) {
                context.append("Книга: ").append(book.getTitle()).append("\n");
                if (book.getDescription() != null) {
                    context.append("Описание: ").append(book.getDescription()).append("\n");
                }
                context.append("\n");
            }
        } catch (Exception e) {
            logger.warn("Не удалось построить контекст из книг пользователя: {}", e.getMessage());
        }
        
        return context.toString();
    }
    
    /**
     * Проверка доступности AI сервиса
     */
    public boolean isAIServiceAvailable() {
        try {
            String healthUrl = aiServiceUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("AI сервис недоступен: {}", e.getMessage());
            return false;
        }
    }
}