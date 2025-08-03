package com.example.studentbot.repository;

import com.example.studentbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с пользователями
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Найти пользователя по Telegram ID
     */
    Optional<User> findByTelegramId(Long telegramId);
    
    /**
     * Найти пользователя по username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Найти активных пользователей
     */
    List<User> findByStatus(User.UserStatus status);
    
    /**
     * Найти пользователей, зарегистрированных после определенной даты
     */
    List<User> findByRegistrationDateAfter(LocalDateTime date);
    
    /**
     * Найти пользователей по языковому коду
     */
    List<User> findByLanguageCode(String languageCode);
    
    /**
     * Найти пользователей с активностью после определенной даты
     */
    @Query("SELECT u FROM User u WHERE u.lastActivityDate > :date")
    List<User> findActiveUsersSince(@Param("date") LocalDateTime date);
    
    /**
     * Подсчитать общее количество активных пользователей
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    Long countByStatus(@Param("status") User.UserStatus status);
    
    /**
     * Найти пользователей по части имени или фамилии
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> findByNameContaining(@Param("name") String name);
}