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
 * Сервис для работы с пользователями
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
     * Получить пользователя по username
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
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
     * Обновить предпочтения пользователя
     */
    public void updateUserPreferences(Long telegramId, String preferences) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            user.get().setPreferences(preferences);
            userRepository.save(user.get());
            logger.info("Обновлены предпочтения пользователя: {}", telegramId);
        }
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
     * Получить активных пользователей за последние дни
     */
    @Transactional(readOnly = true)
    public List<User> getActiveUsersSince(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return userRepository.findActiveUsersSince(cutoffDate);
    }
    
    /**
     * Поиск пользователей по имени
     */
    @Transactional(readOnly = true)
    public List<User> searchUsersByName(String name) {
        return userRepository.findByNameContaining(name);
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
     * Получить статистику пользователей
     */
    @Transactional(readOnly = true)
    public UserStatistics getUserStatistics() {
        Long totalUsers = userRepository.count();
        Long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);
        Long blockedUsers = userRepository.countByStatus(User.UserStatus.BLOCKED);
        
        return new UserStatistics(totalUsers, activeUsers, blockedUsers);
    }
    
    /**
     * Удалить неактивных пользователей (старше N дней)
     */
    public int cleanupInactiveUsers(int inactiveDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(inactiveDays);
        List<User> inactiveUsers = userRepository.findAll().stream()
                .filter(user -> user.getLastActivityDate().isBefore(cutoffDate))
                .filter(user -> user.getStatus() != User.UserStatus.ACTIVE)
                .toList();
        
        userRepository.deleteAll(inactiveUsers);
        
        logger.info("Удалено {} неактивных пользователей", inactiveUsers.size());
        return inactiveUsers.size();
    }
    
    /**
     * Класс для статистики пользователей
     */
    public static class UserStatistics {
        private final Long totalUsers;
        private final Long activeUsers;
        private final Long blockedUsers;
        
        public UserStatistics(Long totalUsers, Long activeUsers, Long blockedUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.blockedUsers = blockedUsers;
        }
        
        public Long getTotalUsers() { return totalUsers; }
        public Long getActiveUsers() { return activeUsers; }
        public Long getBlockedUsers() { return blockedUsers; }
    }
}