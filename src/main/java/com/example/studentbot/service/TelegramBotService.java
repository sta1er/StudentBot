package com.example.studentbot.service;

import com.example.studentbot.model.ChatHistory;
import com.example.studentbot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Основной сервис Telegram бота
 */
@Component
public class TelegramBotService extends TelegramLongPollingBot {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${telegram.bot.username}")
    private String botUsername;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AIService aiService;
    
    @Autowired
    private BookService bookService;
    
    @Override
    public String getBotToken() {
        return botToken;
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке обновления: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Обработка входящих сообщений
     */
    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String messageText = message.getText();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = message.getFrom();
        
        // Регистрация или обновление пользователя
        User user = userService.registerOrUpdateUser(
            telegramUser.getId(),
            telegramUser.getFirstName(),
            telegramUser.getLastName(),
            telegramUser.getUserName(),
            telegramUser.getLanguageCode()
        );
        
        logger.info("Получено сообщение от пользователя {}: {}", user.getTelegramId(), messageText);
        
        // Обработка команд
        if (messageText != null && messageText.startsWith("/")) {
            handleCommand(chatId, messageText, user);
        } else if (message.hasDocument()) {
            handleDocument(chatId, message, user);
        } else if (messageText != null) {
            handleTextMessage(chatId, messageText, user);
        }
    }
    
    /**
     * Обработка команд
     */
    private void handleCommand(Long chatId, String command, User user) {
        switch (command.toLowerCase()) {
            case "/start":
                handleStartCommand(chatId, user);
                break;
            case "/help":
                handleHelpCommand(chatId);
                break;
            case "/books":
                handleBooksCommand(chatId, user);
                break;
            case "/history":
                handleHistoryCommand(chatId, user);
                break;
            case "/settings":
                handleSettingsCommand(chatId, user);
                break;
            default:
                sendMessage(chatId, "Неизвестная команда. Используйте /help для списка доступных команд.");
        }
    }
    
    /**
     * Обработка команды /start
     */
    private void handleStartCommand(Long chatId, User user) {
        String welcomeMessage = String.format(
            "Привет, %s! 👋\n\n" +
            "Я твой помощник для изучения материалов. Вот что я умею:\n\n" +
            "📚 Отвечать на вопросы по загруженным книгам\n" +
            "💡 Объяснять сложные концепции\n" +
            "📝 Помогать с домашними заданиями\n" +
            "🔍 Искать информацию в материалах\n\n" +
            "Загрузи документ или задай вопрос!",
            user.getFirstName()
        );
        
        sendMessageWithKeyboard(chatId, welcomeMessage, createMainMenuKeyboard());
    }
    
    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(Long chatId) {
        String helpMessage = 
            "🤖 Доступные команды:\n\n" +
            "/start - Начать работу с ботом\n" +
            "/help - Показать это сообщение\n" +
            "/books - Список загруженных книг\n" +
            "/history - История ваших вопросов\n" +
            "/settings - Настройки профиля\n\n" +
            "📌 Просто отправьте мне:\n" +
            "• Текстовый вопрос для получения ответа\n" +
            "• PDF, DOCX или TXT файл для загрузки\n" +
            "• Команду для выполнения действия";
        
        sendMessage(chatId, helpMessage);
    }
    
    /**
     * Обработка команды /books
     */
    private void handleBooksCommand(Long chatId, User user) {
        try {
            var books = bookService.getUserBooks(user.getTelegramId());
            if (books.isEmpty()) {
                sendMessage(chatId, "У вас пока нет загруженных книг. Отправьте документ для загрузки!");
            } else {
                StringBuilder message = new StringBuilder("📚 Ваши книги:\n\n");
                books.forEach(book -> {
                    message.append(String.format("📖 %s\n", book.getTitle()));
                    if (book.getAuthor() != null) {
                        message.append(String.format("✍️ %s\n", book.getAuthor()));
                    }
                    message.append(String.format("📅 %s\n\n", 
                        book.getUploadDate().toLocalDate()));
                });
                sendMessage(chatId, message.toString());
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении списка книг: {}", e.getMessage());
            sendMessage(chatId, "Произошла ошибка при загрузке списка книг.");
        }
    }
    
    /**
     * Обработка команды /history
     */
    private void handleHistoryCommand(Long chatId, User user) {
        // Реализация отображения истории
        sendMessage(chatId, "История ваших вопросов будет доступна в следующей версии!");
    }
    
    /**
     * Обработка команды /settings
     */
    private void handleSettingsCommand(Long chatId, User user) {
        // Реализация настроек
        sendMessage(chatId, "Настройки будут доступны в следующей версии!");
    }
    
    /**
     * Обработка текстовых сообщений
     */
    private void handleTextMessage(Long chatId, String messageText, User user) {
        try {
            // Показываем индикатор печати
            sendChatAction(chatId, "typing");
            
            long startTime = System.currentTimeMillis();
            
            // Сохраняем сообщение пользователя в историю
            ChatHistory chatHistory = new ChatHistory(user, messageText, ChatHistory.MessageType.TEXT);
            chatHistory.setStatus(ChatHistory.ProcessingStatus.PROCESSING);
            
            // Получаем ответ от AI сервиса
            String aiResponse = aiService.processTextMessage(messageText, user.getTelegramId());
            
            // Обновляем историю
            long processingTime = System.currentTimeMillis() - startTime;
            chatHistory.setBotResponse(aiResponse);
            chatHistory.setProcessingTimeMs((int) processingTime);
            chatHistory.setStatus(ChatHistory.ProcessingStatus.COMPLETED);
            
            // Отправляем ответ пользователю
            sendMessage(chatId, aiResponse);
            
            logger.info("Обработано сообщение за {} мс", processingTime);
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке текстового сообщения: {}", e.getMessage(), e);
            sendMessage(chatId, "Извините, произошла ошибка при обработке вашего сообщения. Попробуйте еще раз.");
        }
    }
    
    /**
     * Обработка загруженных документов
     */
    private void handleDocument(Long chatId, Message message, User user) {
        try {
            sendMessage(chatId, "📄 Обрабатываю ваш документ...");

            Document document = message.getDocument();
            String fileId = document.getFileId();
            String fileName = document.getFileName();
            Long userId = user.getTelegramId();
            String mimeType = document.getMimeType();
            Long fileSize = document.getFileSize();

            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);
            File telegramFile = execute(getFileMethod);

            java.io.File downloadFile = java.io.File.createTempFile("studentbot-", fileName);
            downloadFile.deleteOnExit();
            downloadFile(telegramFile.getFilePath(), downloadFile);

            try (InputStream inputStream = new FileInputStream(downloadFile)) {
                bookService.uploadDocument(
                        fileId,
                        fileName,
                        userId,
                        inputStream,
                        fileSize != null ? fileSize : downloadFile.length(),
                        mimeType != null ? mimeType : "application/octet-stream"
                );
            }

            sendMessage(chatId, String.format("✅ Документ '%s' успешно загружен! Теперь вы можете задавать вопросы по его содержанию.", fileName));

        } catch (Exception e) {
            logger.error("Ошибка при обработке документа: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Произошла ошибка при загрузке документа. Попробуйте еще раз.");
        }
    }


    /**
     * Обработка callback запросов
     */
    private void handleCallbackQuery(Update update) {
        // Реализация обработки кнопок
    }
    
    /**
     * Отправка простого сообщения
     */
    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Отправка сообщения с клавиатурой
     */
    private void sendMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения с клавиатурой: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Отправка индикатора печати
     */
    private void sendChatAction(Long chatId, String action) {
        // Реализация отправки chat action
    }
    
    /**
     * Создание главного меню
     */
    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton booksButton = new InlineKeyboardButton();
        booksButton.setText("📚 Мои книги");
        booksButton.setCallbackData("books");
        row1.add(booksButton);
        
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Помощь");
        helpButton.setCallbackData("help");
        row1.add(helpButton);
        
        keyboard.add(row1);
        markup.setKeyboard(keyboard);
        
        return markup;
    }
}