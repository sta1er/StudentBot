package com.example.studentbot.service;

import com.example.studentbot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/**
 * Сервис для проверки подписки пользователей на обязательные каналы
 * и управления правами доступа согласно тарифному плану
 */
@Service
public class SubscriptionValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionValidationService.class);
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${channel.required:@chota_study}")
    private String requiredChannel;
    
    @Value("${channel.enabled:true}")
    private boolean channelCheckEnabled;
    
    private final HttpClient httpClient;
    
    public SubscriptionValidationService() {
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Проверяет, имеет ли пользователь доступ к функциям бота
     * @param user пользователь для проверки
     * @return true если доступ разрешен, false если нужна подписка
     */
    public boolean hasAccess(User user) {
        // Проверяем, не заблокирован ли пользователь
        if (user.getStatus() == User.UserStatus.BLOCKED) {
            logger.warn("User {} is blocked", user.getTelegramId());
            return false;
        }
        
        // Если проверка канала отключена - разрешаем доступ
        if (!channelCheckEnabled) {
            return true;
        }
        
        // Для Premium/Business пользователей доступ всегда есть
        if (user.getSubscriptionTier() != User.SubscriptionTier.FREE) {
            logger.debug("User {} has premium subscription: {}", user.getTelegramId(), user.getSubscriptionTier());
            return true;
        }
        
        // Для FREE пользователей проверяем подписку на канал
        return isUserSubscribedToChannel(user.getTelegramId());
    }
    
    /**
     * Проверяет подписку пользователя на обязательный канал через Telegram Bot API
     * @param userId ID пользователя Telegram
     * @return true если подписан, false если нет
     */
    public boolean isUserSubscribedToChannel(Long userId) {
        if (!channelCheckEnabled) {
            return true;
        }
        
        try {
            String url = String.format("https://api.telegram.org/bot%s/getChatMember?chat_id=%s&user_id=%d", 
                                     botToken, requiredChannel, userId);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
                
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                
                // Проверяем статус пользователя в канале
                boolean isSubscribed = responseBody.contains("\"status\":\"member\"") || 
                                     responseBody.contains("\"status\":\"administrator\"") || 
                                     responseBody.contains("\"status\":\"creator\"");
                
                logger.debug("Subscription check for user {}: {}", userId, isSubscribed);
                return isSubscribed;
            } else {
                logger.warn("Failed to check subscription for user {}: HTTP {}", userId, response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error checking subscription for user {} on channel {}: {}", userId, requiredChannel, e.getMessage());
            // В случае ошибки считаем, что подписка отсутствует
            return false;
        }
    }
    
    /**
     * Нужно ли пользователю подписываться на канал
     * @param user пользователь
     * @return true если нужна подписка, false если нет
     */
    public boolean needsChannelSubscription(User user) {
        if (!channelCheckEnabled) {
            return false;
        }
        
        // Проверяем только для FREE пользователей
        if (user.getSubscriptionTier() != User.SubscriptionTier.FREE) {
            return false;
        }
        
        return !isUserSubscribedToChannel(user.getTelegramId());
    }
    
    /**
     * Получить информацию о статусе доступа пользователя
     * @param user пользователь
     * @return объект с информацией о доступе
     */
    public AccessStatus getAccessStatus(User user) {
        boolean hasAccess = hasAccess(user);
        boolean needsSubscription = needsChannelSubscription(user);
        
        return new AccessStatus(
            hasAccess,
            needsSubscription,
            user.getSubscriptionTier(),
            requiredChannel,
            "https://t.me/" + requiredChannel.replace("@", "")
        );
    }
    
    /**
     * Класс для информации о статусе доступа
     */
    public static class AccessStatus {
        private final boolean hasAccess;
        private final boolean needsSubscription;
        private final User.SubscriptionTier subscriptionTier;
        private final String requiredChannel;
        private final String channelUrl;
        
        public AccessStatus(boolean hasAccess, boolean needsSubscription, 
                          User.SubscriptionTier subscriptionTier, 
                          String requiredChannel, String channelUrl) {
            this.hasAccess = hasAccess;
            this.needsSubscription = needsSubscription;
            this.subscriptionTier = subscriptionTier;
            this.requiredChannel = requiredChannel;
            this.channelUrl = channelUrl;
        }
        
        // Getters
        public boolean hasAccess() { return hasAccess; }
        public boolean needsSubscription() { return needsSubscription; }
        public User.SubscriptionTier getSubscriptionTier() { return subscriptionTier; }
        public String getRequiredChannel() { return requiredChannel; }
        public String getChannelUrl() { return channelUrl; }
    }
}