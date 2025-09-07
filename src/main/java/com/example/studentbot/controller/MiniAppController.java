package com.example.studentbot.controller;

import com.example.studentbot.dto.MiniAppUserInfoDto;
import com.example.studentbot.dto.FileUploadResponseDto;
import com.example.studentbot.dto.UserBooksResponseDto;
import com.example.studentbot.service.UserService;
import com.example.studentbot.service.BookService;
import com.example.studentbot.service.TelegramMiniAppAuthService;
import com.example.studentbot.service.SubscriptionValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/miniapp")
@CrossOrigin(origins = "*")
public class MiniAppController {

    private static final Logger logger = LoggerFactory.getLogger(MiniAppController.class);

    @Autowired
    private TelegramMiniAppAuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private BookService bookService;

    @Autowired
    private SubscriptionValidationService subscriptionValidationService;

    /**
     * Аутентификация пользователя и получение информации
     */
    @PostMapping("/auth")
    public ResponseEntity authenticate(@RequestBody Map<String, String> request) {
        try {
            String initData = request.get("initData");
            if (initData == null || initData.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Проверяем подпись Telegram
            if (!authService.validateTelegramData(initData)) {
                return ResponseEntity.status(401).build();
            }

            // Извлекаем данные пользователя
            var userInfo = authService.extractUserInfo(initData);

            // Регистрируем или обновляем пользователя
            var user = userService.registerOrUpdateUser(
                    userInfo.getId(),
                    userInfo.getFirstName(),
                    userInfo.getLastName(),
                    userInfo.getUsername(),
                    userInfo.getLanguageCode()
            );

            // Проверяем доступ пользователя
            var accessStatus = subscriptionValidationService.getAccessStatus(user);

            if (!accessStatus.hasAccess()) {
                logger.warn("User {} doesn't have access - subscription required", user.getTelegramId());

                // Возвращаем специальный ответ о необходимости подписки
                Map<String, Object> subscriptionRequired = new HashMap<>();
                subscriptionRequired.put("success", false);
                subscriptionRequired.put("error", "SUBSCRIPTION_REQUIRED");
                subscriptionRequired.put("message", "Для использования бота необходимо подписаться на канал " + accessStatus.getRequiredChannel());
                subscriptionRequired.put("channelUrl", accessStatus.getChannelUrl());
                subscriptionRequired.put("channelName", accessStatus.getRequiredChannel());
                subscriptionRequired.put("subscriptionTier", user.getSubscriptionTier().name());
                subscriptionRequired.put("userName", user.getFirstName());

                return ResponseEntity.status(403).body(subscriptionRequired);
            }

            // Если доступ есть - возвращаем обычный ответ
            var response = new MiniAppUserInfoDto(
                    user.getTelegramId(),
                    user.getFirstName(),
                    user.getSubscriptionTier().name(),
                    user.getUploadedBooksCount(),
                    user.getMaxBooks(),
                    user.getMaxFileSizeMB()
            );

            logger.info("Успешная аутентификация пользователя: {}", user.getTelegramId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Ошибка аутентификации: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Проверка подписки на канал (новый endpoint)
     */
    @PostMapping("/check-subscription")
    public ResponseEntity<Map<String, Object>> checkSubscription(@RequestBody Map<String, Long> request) {
        try {
            Long telegramId = request.get("telegramId");
            if (telegramId == null) {
                return ResponseEntity.badRequest().build();
            }

            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();
            var accessStatus = subscriptionValidationService.getAccessStatus(user);

            Map<String, Object> response = new HashMap<>();

            if (accessStatus.hasAccess()) {
                response.put("subscribed", true);
                response.put("message", user.getSubscriptionTier() == com.example.studentbot.model.User.SubscriptionTier.FREE ?
                        "Спасибо за подписку на " + accessStatus.getRequiredChannel() + "!" :
                        "У вас Premium доступ!");
            } else {
                response.put("subscribed", false);
                response.put("message", "Подписка на канал " + accessStatus.getRequiredChannel() + " не найдена");
                response.put("channelUrl", accessStatus.getChannelUrl());
                response.put("channelName", accessStatus.getRequiredChannel());
            }

            response.put("subscriptionTier", user.getSubscriptionTier().name());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Ошибка проверки подписки: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Загрузка файла
     */
    @PostMapping("/upload")
    public ResponseEntity uploadFile(@RequestParam("file") MultipartFile file,
                                     @RequestParam("telegramId") Long telegramId) {
        try {
            // Проверяем пользователя
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();

            // Проверяем доступ пользователя
            if (!subscriptionValidationService.hasAccess(user)) {
                var accessStatus = subscriptionValidationService.getAccessStatus(user);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "SUBSCRIPTION_REQUIRED");
                errorResponse.put("message", "Необходимо подписаться на канал " + accessStatus.getRequiredChannel());
                errorResponse.put("channelUrl", accessStatus.getChannelUrl());
                errorResponse.put("channelName", accessStatus.getRequiredChannel());
                return ResponseEntity.status(403).body(errorResponse);
            }

            // Проверяем лимиты
            if (!user.canUploadMoreBooks()) {
                return ResponseEntity.status(409).build(); // Conflict - лимит достигнут
            }

            if (!user.canUploadFile(file.getSize())) {
                return ResponseEntity.status(413).build(); // Payload Too Large
            }

            // Проверяем тип файла
            if (!isValidFileType(file)) {
                return ResponseEntity.status(415).build(); // Unsupported Media Type
            }

            // Загружаем файл
            bookService.uploadDocument(
                    null,
                    file.getOriginalFilename(),
                    telegramId,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );

            // Обновляем счетчик
            user.incrementUploadedBooks();
            userService.updateUser(user);

            var response = new FileUploadResponseDto(
                    "File uploaded successfully",
                    file.getOriginalFilename(),
                    user.getUploadedBooksCount(),
                    user.getMaxBooks()
            );

            logger.info("Файл {} успешно загружен пользователем {}", file.getOriginalFilename(), telegramId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Ошибка загрузки файла: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Получение списка книг пользователя
     */
    @GetMapping("/books/{telegramId}")
    public ResponseEntity getUserBooks(@PathVariable Long telegramId) {
        try {
            // Проверяем доступ пользователя
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isPresent()) {
                var user = userOpt.get();
                if (!subscriptionValidationService.hasAccess(user)) {
                    var accessStatus = subscriptionValidationService.getAccessStatus(user);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "SUBSCRIPTION_REQUIRED");
                    errorResponse.put("message", "Необходимо подписаться на канал " + accessStatus.getRequiredChannel());
                    errorResponse.put("channelUrl", accessStatus.getChannelUrl());
                    errorResponse.put("channelName", accessStatus.getRequiredChannel());
                    return ResponseEntity.status(403).body(errorResponse);
                }
            }

            var books = bookService.getUserBooks(telegramId);
            var response = new UserBooksResponseDto(books);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Ошибка получения книг: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Удаление книги
     */
    @DeleteMapping("/books/{bookId}")
    public ResponseEntity deleteBook(@PathVariable Long bookId, @RequestParam Long telegramId) {
        try {
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();

            // Проверяем доступ пользователя
            if (!subscriptionValidationService.hasAccess(user)) {
                var accessStatus = subscriptionValidationService.getAccessStatus(user);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "SUBSCRIPTION_REQUIRED");
                errorResponse.put("message", "Необходимо подписаться на канал " + accessStatus.getRequiredChannel());
                errorResponse.put("channelUrl", accessStatus.getChannelUrl());
                errorResponse.put("channelName", accessStatus.getRequiredChannel());
                return ResponseEntity.status(403).body(errorResponse);
            }

            var bookOpt = bookService.getBookMetadata(bookId);
            if (bookOpt.isEmpty() || !bookOpt.get().getUploadedBy().equals(telegramId)) {
                return ResponseEntity.status(404).build();
            }

            // Удаляем книгу
            bookService.deleteBook(bookId);

            // Уменьшаем счетчик
            user.decrementUploadedBooks();
            userService.updateUser(user);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Ошибка удаления книги: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    private boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (contentType == null || filename == null) return false;

        return contentType.equals("application/pdf") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                contentType.equals("text/plain") ||
                filename.toLowerCase().endsWith(".pdf") ||
                filename.toLowerCase().endsWith(".docx") ||
                filename.toLowerCase().endsWith(".txt");
    }
}