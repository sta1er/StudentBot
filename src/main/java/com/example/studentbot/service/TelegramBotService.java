package com.example.studentbot.service;

import com.example.studentbot.model.User;
import com.example.studentbot.service.UserService;
import com.example.studentbot.service.SubscriptionValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${miniapp.url}")
    private String miniAppUrl;

    @Autowired
    private UserService userService;

    @Autowired
    private AIService aiService;

    @Autowired
    private SubscriptionValidationService subscriptionValidationService;

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
                handleCallbackQuery(update.getCallbackQuery());
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
        } else if (messageText != null) {
            handleTextMessage(chatId, messageText, user);
        }
    }

    /**
     * Обработка команд
     */
    private void handleCommand(Long chatId, String command, User user) {
        // Команда /start всегда доступна для первичной регистрации
        if ("/start".equals(command.toLowerCase().trim())) {
            handleStartCommand(chatId, user);
            return;
        }

        // Проверяем доступ для всех остальных команд
        if (!checkAccessAndNotify(chatId, user)) {
            return;
        }

        switch (command.toLowerCase().split(" ")[0]) {
            case "/help":
                handleHelpCommand(chatId);
                break;
            case "/upload":
                handleUploadCommand(chatId, user);
                break;
            case "/books":
                handleBooksCommand(chatId, user);
                break;
            case "/subscription":
                handleSubscriptionCommand(chatId, user);
                break;
            default:
                sendMessage(chatId, "❓ Неизвестная команда. Используйте /help для списка доступных команд.");
        }
    }

    /**
     * Обработка команды /start
     */
    private void handleStartCommand(Long chatId, User user) {
        // Проверяем доступ после регистрации
        if (!checkAccessAndNotify(chatId, user)) {
            return;
        }

        String welcomeMessage = String.format(
                "👋 Привет, %s!\n\n" +
                        "🤖 Я твой помощник для изучения материалов с поддержкой ИИ.\n\n" +
                        "📚 Что я умею:\n" +
                        "• Отвечать на вопросы по загруженным книгам\n" +
                        "• Объяснять сложные концепции\n" +
                        "• Помогать с домашними заданиями\n" +
                        "• Создавать краткие содержания\n\n" +
                        "📊 Ваш тарифный план: %s\n" +
                        "📖 Загружено книг: %d из %d\n" +
                        "💾 Максимальный размер файла: %d МБ\n\n" +
                        "%s\n" +
                        "Выберите действие:",
                escapeMarkdownV2(user.getFirstName()),
                user.getSubscriptionTier().name(),
                user.getUploadedBooksCount(),
                user.getMaxBooks(),
                user.getMaxFileSizeMB(),
                user.getSubscriptionTier() == User.SubscriptionTier.FREE ?
                        "🙏 Спасибо за подписку на @chota_study!" :
                        "⭐ Спасибо за Premium подписку!"
        );

        sendMessageWithKeyboard(chatId, welcomeMessage, createMainMenuKeyboard());
    }

    /**
     * Отправка сообщения о необходимости подписки
     * ИСПРАВЛЕНО: убрал проблемные Markdown символы
     */
    private void sendSubscriptionRequiredMessage(Long chatId, User user) {
        var accessStatus = subscriptionValidationService.getAccessStatus(user);

        String message = String.format(
                "🔒 Доступ ограничен\n\n" +
                        "Привет, %s! 👋\n\n" +
                        "Для использования бота на бесплатном тарифе необходимо быть подписанным на наш канал:\n\n" +
                        "📢 %s - полезные материалы для учебы\n\n" +
                        "💡 Что вы получите:\n" +
                        "• Конспекты и шпаргалки\n" +
                        "• Советы по учебе\n" +
                        "• Анонсы новых функций бота\n" +
                        "• Эксклюзивные материалы\n\n" +
                        "После подписки нажмите «Проверить подписку» ✅\n\n" +
                        "🌟 Или оформите Premium подписку и получите доступ без ограничений!",
                escapeMarkdownV2(user.getFirstName()),
                accessStatus.getRequiredChannel()
        );

        sendMessageWithKeyboard(chatId, message, createSubscriptionCheckKeyboard(accessStatus));
    }

    /**
     * Экранирование специальных символов для MarkdownV2
     */
    private String escapeMarkdownV2(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    /**
     * Создание клавиатуры для проверки подписки
     */
    private InlineKeyboardMarkup createSubscriptionCheckKeyboard(SubscriptionValidationService.AccessStatus accessStatus) {
        InlineKeyboardMarkup markup = createKeyboardMarkup();

        addKeyboardRow(markup, createUrlButton("📢 Подписаться на канал", accessStatus.getChannelUrl()));
        addKeyboardRow(markup, createCallbackButton("✅ Проверить подписку", "check_subscription"));
        addKeyboardRow(markup, createCallbackButton("🌟 Получить Premium", "upgrade_subscription"));

        return markup;
    }

    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(Long chatId) {
        String helpMessage =
                "🆘 Помощь\n\n" +
                        "📌 Основные команды:\n" +
                        "/start - Главное меню\n" +
                        "/upload - Загрузить документы\n" +
                        "/books - Мои книги\n" +
                        "/subscription - Управление подпиской\n" +
                        "/help - Эта справка\n\n" +
                        "💡 Как пользоваться:\n" +
                        "1️⃣ Загрузите документы через Mini App\n" +
                        "2️⃣ Задавайте вопросы по загруженным материалам\n" +
                        "3️⃣ Получайте умные ответы от ИИ\n\n" +
                        "📱 Поддерживаемые форматы:\n" +
                        "• PDF документы\n" +
                        "• DOCX файлы\n" +
                        "• TXT файлы\n\n" +
                        "❓ Есть вопросы? Просто напишите мне!";

        sendMessage(chatId, helpMessage);
    }

    /**
     * Обработка команды /upload
     */
    private void handleUploadCommand(Long chatId, User user) {
        if (!user.canUploadMoreBooks()) {
            String message = String.format(
                    "📚 Лимит загрузок исчерпан\n\n" +
                            "Вы достигли максимального количества книг (%d) для тарифа %s.\n\n" +
                            "💡 Удалите ненужные книги или обновите тариф для загрузки новых материалов.",
                    user.getMaxBooks(),
                    user.getSubscriptionTier().name()
            );

            sendMessageWithKeyboard(chatId, message, createSubscriptionKeyboard());
            return;
        }

        String message = String.format(
                "📤 Загрузка документов\n\n" +
                        "Используйте приложение для загрузки документов:\n\n" +
                        "📊 Ваши лимиты:\n" +
                        "• Загружено: %d из %d книг\n" +
                        "• Максимальный размер: %d МБ\n" +
                        "• Поддерживаемые форматы: PDF, DOCX, TXT",
                user.getUploadedBooksCount(),
                user.getMaxBooks(),
                user.getMaxFileSizeMB()
        );

        sendMessageWithKeyboard(chatId, message, createUploadKeyboard());
    }

    /**
     * Обработка команды /books
     */
    private void handleBooksCommand(Long chatId, User user) {
        String message = "📚 Мои книги\n\nДля управления книгами используйте приложение:";
        sendMessageWithKeyboard(chatId, message, createBooksKeyboard());
    }

    /**
     * Обработка команды /subscription
     */
    private void handleSubscriptionCommand(Long chatId, User user) {
        String expiryInfo = user.getSubscriptionExpiryDate() != null
                ? String.format("\n⏱ Действует до: %s",
                user.getSubscriptionExpiryDate().toLocalDate().toString())
                : "";

        String message = String.format(
                "💳 Ваша подписка\n\n" +
                        "📦 Текущий тариф: %s%s\n\n" +
                        "📊 Возможности:\n" +
                        "• Максимум книг: %d\n" +
                        "• Размер файла: до %d МБ\n\n" +
                        "💰 Доступные тарифы:\n" +
                        "🆓 FREE - 5 книг, 100 МБ\n" +
                        "⭐ PREMIUM - 25 книг, 500 МБ - $9.99/мес\n" +
                        "🚀 BUSINESS - 100 книг, 1000 МБ - $29.99/мес",
                user.getSubscriptionTier().name(),
                expiryInfo,
                user.getMaxBooks(),
                user.getMaxFileSizeMB()
        );

        sendMessageWithKeyboard(chatId, message, createSubscriptionKeyboard());
    }

    /**
     * Обработка текстовых сообщений
     */
    private void handleTextMessage(Long chatId, String messageText, User user) {
        // Проверяем доступ перед обработкой текстовых сообщений
        if (!checkAccessAndNotify(chatId, user)) {
            return;
        }

        try {
            sendChatAction(chatId, "typing");
            // Получаем ответ от AI сервиса
            String aiResponse = aiService.processTextMessage(messageText, user.getTelegramId());
            sendMessage(chatId, aiResponse);
        } catch (Exception e) {
            logger.error("Ошибка при обработке текстового сообщения: {}", e.getMessage(), e);
            sendMessage(chatId, "😔 Извините, произошла ошибка при обработке вашего сообщения. Попробуйте еще раз.");
        }
    }

    /**
     * Обработка callback запросов
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();

        try {
            switch (data) {
                case "check_subscription":
                    handleCheckSubscription(chatId, userId);
                    break;
                case "open_upload_app":
                    // Проверяем доступ перед открытием приложения
                    checkAccessAndNotify(chatId, userId);
                    break;
                case "view_books":
                    // Проверяем доступ перед открытием приложения
                    checkAccessAndNotify(chatId, userId);
                    break;
                case "upgrade_subscription":
                    handleSubscriptionCommand(chatId, userService.getUserByTelegramId(userId).orElse(null));
                    break;
            }

            // Подтверждаем получение callback
            answerCallbackQuery(callbackQuery.getId());
        } catch (Exception e) {
            logger.error("Ошибка при обработке callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка проверки подписки
     */
    private void handleCheckSubscription(Long chatId, Long userId) {
        try {
            User user = userService.getUserByTelegramId(userId).orElse(null);
            if (user == null) {
                sendMessage(chatId, "❌ Пользователь не найден");
                return;
            }

            if (subscriptionValidationService.hasAccess(user)) {
                // Пользователь подписался - показываем главное меню
                String successMessage = String.format(
                        "✅ Отлично, %s!\n\n" +
                                "🎉 Подписка на канал @chota_study подтверждена!\n\n" +
                                "Теперь вы можете пользоваться всеми функциями бота на бесплатном тарифе.\n\n" +
                                "📚 Ваши возможности:\n" +
                                "• Загрузка до %d книг\n" +
                                "• Файлы до %d МБ\n" +
                                "• Вопросы по материалам\n" +
                                "• ИИ помощник\n\n" +
                                "Добро пожаловать! 🚀",
                        escapeMarkdownV2(user.getFirstName()),
                        user.getMaxBooks(),
                        user.getMaxFileSizeMB()
                );

                sendMessageWithKeyboard(chatId, successMessage, createMainMenuKeyboard());
            } else {
                // Пользователь все еще не подписан
                var accessStatus = subscriptionValidationService.getAccessStatus(user);
                String notSubscribedMessage = String.format(
                        "❌ Подписка не найдена\n\n" +
                                "%s, похоже вы еще не подписались на канал %s\n\n" +
                                "🔄 Что делать:\n" +
                                "1. Нажмите кнопку «Подписаться на канал»\n" +
                                "2. Подпишитесь на канал %s\n" +
                                "3. Вернитесь и нажмите «Проверить подписку»\n\n" +
                                "💡 Или оформите Premium и пользуйтесь без ограничений!",
                        escapeMarkdownV2(user.getFirstName()),
                        accessStatus.getRequiredChannel(),
                        accessStatus.getRequiredChannel()
                );

                sendMessageWithKeyboard(chatId, notSubscribedMessage, createSubscriptionCheckKeyboard(accessStatus));
            }
        } catch (Exception e) {
            logger.error("Ошибка при проверке подписки для пользователя {}: {}", userId, e.getMessage(), e);
            sendMessage(chatId, "❌ Произошла ошибка при проверке подписки. Попробуйте еще раз через несколько секунд.");
        }
    }

    /**
     * Создание главного меню
     */
    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup markup = createKeyboardMarkup();

        addKeyboardRow(markup, createWebAppButton("📤 Загрузить книги", miniAppUrl));
        addKeyboardRow(markup,
                createWebAppButton("📚 Мои книги", miniAppUrl + "?tab=books"),
                createCallbackButton("💳 Подписка", "upgrade_subscription")
        );

        return markup;
    }

    /**
     * Создание клавиатуры для загрузки
     */
    private InlineKeyboardMarkup createUploadKeyboard() {
        InlineKeyboardMarkup markup = createKeyboardMarkup();
        addKeyboardRow(markup, createWebAppButton("📱 Открыть приложение", miniAppUrl));
        return markup;
    }

    /**
     * Создание клавиатуры для книг
     */
    private InlineKeyboardMarkup createBooksKeyboard() {
        InlineKeyboardMarkup markup = createKeyboardMarkup();
        addKeyboardRow(markup, createWebAppButton("📱 Управление книгами", miniAppUrl + "?tab=books"));
        return markup;

    }

    /**
     * Создание клавиатуры для подписки
     */
    private InlineKeyboardMarkup createSubscriptionKeyboard() {
        InlineKeyboardMarkup markup = createKeyboardMarkup();

        addKeyboardRow(markup, createCallbackButton("⭐ PREMIUM - $9.99", "buy_premium"));
        addKeyboardRow(markup, createCallbackButton("🚀 BUSINESS - $29.99", "buy_business"));

        return markup;
    }

    /**
     * Создание кнопки Web App
     */
    private InlineKeyboardButton createWebAppButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        WebAppInfo webAppInfo = new WebAppInfo(url);
        button.setWebApp(webAppInfo);
        return button;
    }

    /**
     * Отправка простого сообщения
     */
    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        // Убираем parseMode по умолчанию, чтобы избежать ошибок парсинга
        // message.setParseMode("Markdown");

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
        // Убираем parseMode по умолчанию, чтобы избежать ошибок парсинга
        // message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения с клавиатурой: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка chat action
     */
    private void sendChatAction(Long chatId, String action) {
        // Реализация отправки chat action
    }

    /**
     * Ответ на callback query
     */
    private void answerCallbackQuery(String callbackQueryId) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при ответе на callback query: {}", e.getMessage());
        }
    }

    /**
     * Проверяет доступ пользователя и автоматически отправляет сообщение о необходимости подписки
     * @return true если доступ есть, false если нет (и сообщение уже отправлено)
     */
    private boolean checkAccessAndNotify(Long chatId, Long userId) {
        User user = userService.getUserByTelegramId(userId).orElse(null);
        return checkAccessAndNotify(chatId, user);
    }

    /**
     * Перегруженная версия для случаев, когда User уже получен
     */
    private boolean checkAccessAndNotify(Long chatId, User user) {
        if (user != null && !subscriptionValidationService.hasAccess(user)) {
            sendSubscriptionRequiredMessage(chatId, user);
            return false;
        }
        return true;
    }

    /**
     * Создает базовую разметку клавиатуры
     */
    private InlineKeyboardMarkup createKeyboardMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(new ArrayList<>());
        return markup;
    }

    /**
     * Добавляет ряд кнопок в клавиатуру
     */
    private void addKeyboardRow(InlineKeyboardMarkup markup, InlineKeyboardButton... buttons) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (InlineKeyboardButton button : buttons) {
            row.add(button);
        }
        markup.getKeyboard().add(row);
    }

    /**
     * Создает кнопку с текстом и callback data
     */
    private InlineKeyboardButton createCallbackButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Создает кнопку с URL
     */
    private InlineKeyboardButton createUrlButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        return button;
    }
}