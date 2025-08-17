package com.example.studentbot.controller;

import com.example.studentbot.dto.MiniAppUserInfoDto;
import com.example.studentbot.dto.FileUploadResponseDto;
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

/**
 * REST контроллер для Telegram Mini App
 */
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
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> request) {
        try {
            String initData = request.get("initData");
            
            if (initData == null || initData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Init data is required"));
            }

            // Проверяем подпись Telegram
            if (!authService.validateTelegramData(initData)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Telegram signature"));
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
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user", response
            ));
        } catch (Exception e) {
            logger.error("Ошибка аутентификации: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Authentication failed"));
        }
    }

    /**
     * Загрузка файла
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam("telegramId") Long telegramId) {
        try {
            // Проверяем пользователя
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            var user = userOpt.get();

            // Проверяем лимиты
            if (!user.canUploadMoreBooks()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Book limit reached", 
                    "message", String.format("You can upload maximum %d books with your current plan", user.getMaxBooks())
                ));
            }

            if (!user.canUploadFile(file.getSize())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "File too large",
                    "message", String.format("Maximum file size is %d MB for your plan", user.getMaxFileSizeMB())
                ));
            }

            // Проверяем тип файла
            if (!isValidFileType(file)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid file type",
                    "message", "Only PDF, DOCX, TXT files are allowed"
                ));
            }

            // Загружаем файл
            bookService.uploadDocument(
                null, // fileId не нужен для прямой загрузки
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
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed"));
        }
    }

    /**
     * Получение списка книг пользователя
     */
    @GetMapping("/books/{telegramId}")
    public ResponseEntity<?> getUserBooks(@PathVariable Long telegramId) {
        try {
            var books = bookService.getUserBooks(telegramId);
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            logger.error("Ошибка получения книг: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch books"));
        }
    }

    /**
     * Удаление книги
     */
    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<?> deleteBook(@PathVariable Long bookId, @RequestParam Long telegramId) {
        try {
            var userOpt = userService.getUserByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            var user = userOpt.get();
            var bookOpt = bookService.getBookMetadata(bookId);
            
            if (bookOpt.isEmpty() || !bookOpt.get().getUploadedBy().equals(telegramId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Book not found or not owned by user"));
            }

            // Удаляем книгу
            bookService.deleteBook(bookId);
            
            // Уменьшаем счетчик
            user.decrementUploadedBooks();
            userService.updateUser(user);

            return ResponseEntity.ok(Map.of("message", "Book deleted successfully"));

        } catch (Exception e) {
            logger.error("Ошибка удаления книги: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete book"));
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