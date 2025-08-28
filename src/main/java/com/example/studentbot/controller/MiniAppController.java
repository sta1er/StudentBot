package com.example.studentbot.controller;

import com.example.studentbot.dto.MiniAppUserInfoDto;
import com.example.studentbot.dto.FileUploadResponseDto;
import com.example.studentbot.dto.UserBooksResponseDto;
import com.example.studentbot.service.UserService;
import com.example.studentbot.service.BookService;
import com.example.studentbot.service.TelegramMiniAppAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    /**
     * Аутентификация пользователя и получение информации
     */
    @PostMapping("/auth")
    public ResponseEntity<MiniAppUserInfoDto> authenticate(@RequestBody Map<String, String> request) {
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

            // Возвращаем информацию для фронтенда
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
     * Загрузка файла
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponseDto> uploadFile(@RequestParam("file") MultipartFile file,
                                                            @RequestParam("telegramId") Long telegramId) {
        try {
            // Проверяем пользователя
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();

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
    public ResponseEntity<UserBooksResponseDto> getUserBooks(@PathVariable Long telegramId) {
        try {
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
    public ResponseEntity<Void> deleteBook(@PathVariable Long bookId, @RequestParam Long telegramId) {
        try {
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var user = userOpt.get();
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