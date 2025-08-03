package com.example.studentbot.config;

import com.example.studentbot.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Конфигурация Telegram бота
 */
@Configuration
public class TelegramBotConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotConfig.class);
    
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService) {
        TelegramBotsApi botsApi = null;
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotService);
            logger.info("Telegram бот успешно зарегистрирован");
        } catch (TelegramApiException e) {
            logger.error("Ошибка при регистрации Telegram бота: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось зарегистрировать Telegram бота", e);
        }
        return botsApi;
    }
}