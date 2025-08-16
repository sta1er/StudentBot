package com.example.studentbot.service;

import com.example.studentbot.model.User;
import com.example.studentbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Обновленный сервис для работы с пользователями с поддержкой лимитов
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * Регистрация нового пользователя или обновление существующего
     */
    public User registerOrUpdateUser(Long telegramId, String firstName, String lastName,
                                     String username, String languageCode) {
        Optional<User> existingUser = userRepository.findByTelegramId(telegramId);

        if (existingUser.isPresent()) {
            // Обновляем информацию о существующем пользователе
            User user = existingUser.get();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setLanguageCode(languageCode != null ? languageCode : "ru");
            user.setLastActivityDate(LocalDateTime.now());

            logger.info("Обновлена информация пользователя: {}", telegramId);
            return userRepository.save(user);
        } else {
            // Создаем нового пользователя
            User newUser = new User(telegramId, firstName, lastName, username,
                    languageCode != null ? languageCode : "ru");

            logger.info("Зарегистрирован новый пользователь: {} ({})", firstName, telegramId);
            return userRepository.save(newUser);
        }
    }

    /**
     * Получить пользователя по Telegram ID
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    /**
     * Обновить пользователя
     */
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Обновить время последней активности пользователя
     */
    public void updateLastActivity(Long telegramId) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            user.get().setLastActivityDate(LocalDateTime.now());
            userRepository.save(user.get());
        }
    }

    /**
     * Обновить подписку пользователя
     */
    public void updateUserSubscription(Long telegramId, User.SubscriptionTier newTier) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.updateSubscriptionTier(newTier);

            // Устанавливаем дату окончания подписки (30 дней для премиум тарифов)
            if (newTier != User.SubscriptionTier.FREE) {
                user.setSubscriptionExpiryDate(LocalDateTime.now().plusDays(30));
            } else {
                user.setSubscriptionExpiryDate(null);
            }

            userRepository.save(user);
            logger.info("Обновлена подписка пользователя {} на тариф {}", telegramId, newTier);
        }
    }

    /**
     * Проверить и обновить истекшие подписки
     */
    @Transactional
    public void checkExpiredSubscriptions() {
        List<User> expiredUsers = userRepository.findAll().stream()
                .filter(user -> user.getSubscriptionExpiryDate() != null)
                .filter(user -> user.getSubscriptionExpiryDate().isBefore(LocalDateTime.now()))
                .toList();

        for (User user : expiredUsers) {
            user.updateSubscriptionTier(User.SubscriptionTier.FREE);
            user.setSubscriptionExpiryDate(null);
            userRepository.save(user);
            logger.info("Подписка пользователя {} истекла, переведен на тариф FREE", user.getTelegramId());
        }
    }

    /**
     * Получить статистику использования пользователем
     */
    @Transactional(readOnly = true)
    public UserUsageStats getUserUsageStats(Long telegramId) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        return new UserUsageStats(
                user.getUploadedBooksCount(),
                user.getMaxBooks(),
                user.getMaxFileSizeMB(),
                user.getSubscriptionTier(),
                user.getSubscriptionExpiryDate()
        );
    }

    /**
     * Получить всех активных пользователей
     */
    @Transactional(readOnly = true)
    public List<User> getActiveUsers() {
        return userRepository.findByStatus(User.UserStatus.ACTIVE);
    }

    /**
     * Получить пользователей по языковому коду
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByLanguage(String languageCode) {
        return userRepository.findByLanguageCode(languageCode);
    }

    /**
     * Заблокировать пользователя
     */
    public void blockUser(Long telegramId) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            user.get().setStatus(User.UserStatus.BLOCKED);
            userRepository.save(user.get());
            logger.warn("Пользователь {} заблокирован", telegramId);
        }
    }

    /**
     * Разблокировать пользователя
     */
    public void unblockUser(Long telegramId) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            user.get().setStatus(User.UserStatus.ACTIVE);
            userRepository.save(user.get());
            logger.info("Пользователь {} разблокирован", telegramId);
        }
    }

    /**
     * Проверить, заблокирован ли пользователь
     */
    @Transactional(readOnly = true)
    public boolean isUserBlocked(Long telegramId) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        return user.map(u -> u.getStatus() == User.UserStatus.BLOCKED).orElse(false);
    }

    /**
     * Класс для статистики использования пользователя
     */
    public static class UserUsageStats {
        private final Integer currentBooksCount;
        private final Integer maxBooks;
        private final Long maxFileSizeMB;
        private final User.SubscriptionTier subscriptionTier;
        private final LocalDateTime subscriptionExpiryDate;

        public UserUsageStats(Integer currentBooksCount, Integer maxBooks, Long maxFileSizeMB,
                              User.SubscriptionTier subscriptionTier, LocalDateTime subscriptionExpiryDate) {
            this.currentBooksCount = currentBooksCount;
            this.maxBooks = maxBooks;
            this.maxFileSizeMB = maxFileSizeMB;
            this.subscriptionTier = subscriptionTier;
            this.subscriptionExpiryDate = subscriptionExpiryDate;
        }

        // Getters
        public Integer getCurrentBooksCount() { return currentBooksCount; }
        public Integer getMaxBooks() { return maxBooks; }
        public Long getMaxFileSizeMB() { return maxFileSizeMB; }
        public User.SubscriptionTier getSubscriptionTier() { return subscriptionTier; }
        public LocalDateTime getSubscriptionExpiryDate() { return subscriptionExpiryDate; }
    }
}