package com.example.studentbot.repository;

import com.example.studentbot.model.ChatHistory;
import com.example.studentbot.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с историей чатов
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    
    /**
     * Найти историю чатов пользователя
     */
    List<ChatHistory> findByUserOrderByTimestampDesc(User user);
    
    /**
     * Найти историю чатов пользователя с пагинацией
     */
    Page<ChatHistory> findByUserOrderByTimestampDesc(User user, Pageable pageable);
    
    /**
     * Найти историю чатов пользователя по Telegram ID
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.user.telegramId = :telegramId ORDER BY ch.timestamp DESC")
    List<ChatHistory> findByUserTelegramIdOrderByTimestampDesc(@Param("telegramId") Long telegramId);
    
    /**
     * Найти сообщения по типу
     */
    List<ChatHistory> findByMessageTypeOrderByTimestampDesc(ChatHistory.MessageType messageType);
    
    /**
     * Найти сообщения за определенный период
     */
    List<ChatHistory> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    
    /**
     * Найти сообщения по статусу обработки
     */
    List<ChatHistory> findByStatus(ChatHistory.ProcessingStatus status);
    
    /**
     * Найти сообщения, связанные с книгой
     */
    List<ChatHistory> findByRelatedBookIdOrderByTimestampDesc(String bookId);
    
    /**
     * Найти последние сообщения пользователя
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.user = :user ORDER BY ch.timestamp DESC")
    List<ChatHistory> findRecentByUser(@Param("user") User user);
    
    /**
     * Подсчитать сообщения пользователя
     */
    Long countByUser(User user);
    
    /**
     * Подсчитать сообщения за день
     */
    @Query("SELECT COUNT(ch) FROM ChatHistory ch WHERE DATE(ch.timestamp) = CURRENT_DATE")
    Long countTodaysMessages();
    
    /**
     * Найти сообщения с ошибками
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.status = 'ERROR' ORDER BY ch.timestamp DESC")
    List<ChatHistory> findErrorMessages();
    
    /**
     * Получить статистику по типам сообщений
     */
    @Query("SELECT ch.messageType, COUNT(ch) FROM ChatHistory ch GROUP BY ch.messageType")
    List<Object[]> getMessageTypeStatistics();
    
    /**
     * Найти самые длинные сообщения по времени обработки
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.processingTimeMs IS NOT NULL ORDER BY ch.processingTimeMs DESC")
    List<ChatHistory> findSlowestProcessedMessages();
    
    /**
     * Удалить старые сообщения (старше N дней)
     */
    @Query("DELETE FROM ChatHistory ch WHERE ch.timestamp < :cutoffDate")
    void deleteOldMessages(@Param("cutoffDate") LocalDateTime cutoffDate);
}