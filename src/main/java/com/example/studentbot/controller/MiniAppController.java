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
                logger.warn("Auth request without initData");
                return ResponseEntity.badRequest().build();
            }

            // Проверяем подпись Telegram
            if (!authService.validateTelegramData(initData)) {
                logger.warn("Invalid telegram signature");
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

            // ПРОВЕРКА ДОСТУПА: Проверяем подписку пользователя
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
                subscriptionRequired.put("telegramId", user.getTelegramId()); // ИСПРАВЛЕНО: добавляем telegramId

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
     * Проверка подписки на канал с улучшенным логированием
     */
    @PostMapping("/check-subscription")
    public ResponseEntity<Map<String, Object>> checkSubscription(@RequestBody Map<String, Long> request) {
        Long telegramId = null;

        try {
            telegramId = request.get("telegramId");
            logger.info("Check subscription request for telegramId: {}", telegramId);

            if (telegramId == null) {
                logger.warn("Check subscription request without telegramId");
                return ResponseEntity.badRequest().build();
            }

            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                logger.warn("User not found for telegramId: {}", telegramId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("subscribed", false);
                errorResponse.put("message", "Пользователь не найден");
                return ResponseEntity.ok(errorResponse); // ИСПРАВЛЕНО: возвращаем 200 вместо 400
            }

            var user = userOpt.get();
            var accessStatus = subscriptionValidationService.getAccessStatus(user);

            logger.info("Access status for user {}: hasAccess={}, needsSubscription={}",
                    telegramId, accessStatus.hasAccess(), accessStatus.needsSubscription());

            Map<String, Object> response = new HashMap<>();

            if (accessStatus.hasAccess()) {
                response.put("subscribed", true);
                response.put("message", user.getSubscriptionTier() == com.example.studentbot.model.User.SubscriptionTier.FREE ?
                        "Спасибо за подписку на " + accessStatus.getRequiredChannel() + "!" :
                        "У вас Premium доступ!");

                logger.info("User {} has access", telegramId);
            } else {
                response.put("subscribed", false);
                response.put("message", "Подписка на канал " + accessStatus.getRequiredChannel() + " не найдена");
                response.put("channelUrl", accessStatus.getChannelUrl());
                response.put("channelName", accessStatus.getRequiredChannel());

                logger.info("User {} does not have access", telegramId);
            }

            response.put("subscriptionTier", user.getSubscriptionTier().name());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Ошибка проверки подписки для пользователя {}: {}", telegramId, e.getMessage(), e);

            // Возвращаем структурированную ошибку вместо 500
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("subscribed", false);
            errorResponse.put("message", "Ошибка при проверке подписки. Попробуйте еще раз.");
            return ResponseEntity.ok(errorResponse);
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
                logger.warn("Upload attempt for non-existent user: {}", telegramId);
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();

            // ПРОВЕРКА ДОСТУПА: Проверяем подписку перед загрузкой
            if (!subscriptionValidationService.hasAccess(user)) {
                logger.warn("Upload attempt by user {} without subscription", telegramId);
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
                logger.warn("User {} reached books limit", telegramId);
                return ResponseEntity.status(409).build(); // Conflict - лимит достигнут
            }

            if (!user.canUploadFile(file.getSize())) {
                logger.warn("File too large for user {}: {} bytes", telegramId, file.getSize());
                return ResponseEntity.status(413).build(); // Payload Too Large
            }

            // Проверяем тип файла
            if (!isValidFileType(file)) {
                logger.warn("Invalid file type for user {}: {}", telegramId, file.getContentType());
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
            // ПРОВЕРКА ДОСТУПА: Проверяем подписку перед получением списка книг
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isPresent()) {
                var user = userOpt.get();
                if (!subscriptionValidationService.hasAccess(user)) {
                    logger.warn("Books list request by user {} without subscription", telegramId);
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
                logger.warn("Delete attempt for non-existent user: {}", telegramId);
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();

            // ПРОВЕРКА ДОСТУПА: Проверяем подписку перед удалением
            if (!subscriptionValidationService.hasAccess(user)) {
                logger.warn("Delete attempt by user {} without subscription", telegramId);
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
                logger.warn("Book {} not found or doesn't belong to user {}", bookId, telegramId);
                return ResponseEntity.status(404).build();
            }

            // Удаляем книгу
            bookService.deleteBook(bookId);

            // Уменьшаем счетчик
            user.decrementUploadedBooks();
            userService.updateUser(user);

            logger.info("Book {} deleted by user {}", bookId, telegramId);
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