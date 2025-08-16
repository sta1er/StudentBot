package com.example.studentbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Модель пользователя Telegram бота с поддержкой подписок и лимитов
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long telegramId;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Column(unique = true)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String languageCode;

    @Column(nullable = false)
    private LocalDateTime registrationDate;

    private LocalDateTime lastActivityDate;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // Новые поля для лимитов и подписок
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Column(nullable = false)
    private Integer maxBooks = 5;

    @Column(nullable = false)
    private Long maxFileSizeMB = 100L;

    @Column(nullable = false)
    private Integer uploadedBooksCount = 0;

    private LocalDateTime subscriptionExpiryDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatHistory> chatHistory;

    // Конструкторы
    public User() {
        this.registrationDate = LocalDateTime.now();
        this.lastActivityDate = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.subscriptionTier = SubscriptionTier.FREE;
        this.maxBooks = SubscriptionTier.FREE.getMaxBooks();
        this.maxFileSizeMB = SubscriptionTier.FREE.getMaxFileSizeMB();
    }

    public User(Long telegramId, String firstName, String lastName, String username, String languageCode) {
        this();
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.languageCode = languageCode;
    }

    // Методы для работы с лимитами
    public boolean canUploadMoreBooks() {
        return uploadedBooksCount < maxBooks;
    }

    public boolean canUploadFile(long fileSizeBytes) {
        long fileSizeMB = fileSizeBytes / (1024 * 1024);
        return fileSizeMB <= maxFileSizeMB;
    }

    public void incrementUploadedBooks() {
        this.uploadedBooksCount++;
    }

    public void decrementUploadedBooks() {
        if (this.uploadedBooksCount > 0) {
            this.uploadedBooksCount--;
        }
    }

    // Обновление тарифа
    public void updateSubscriptionTier(SubscriptionTier newTier) {
        this.subscriptionTier = newTier;
        this.maxBooks = newTier.getMaxBooks();
        this.maxFileSizeMB = newTier.getMaxFileSizeMB();
    }

    // Getters and Setters
    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }

    public LocalDateTime getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(LocalDateTime lastActivityDate) { this.lastActivityDate = lastActivityDate; }

    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public SubscriptionTier getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(SubscriptionTier subscriptionTier) { this.subscriptionTier = subscriptionTier; }

    public Integer getMaxBooks() { return maxBooks; }
    public void setMaxBooks(Integer maxBooks) { this.maxBooks = maxBooks; }

    public Long getMaxFileSizeMB() { return maxFileSizeMB; }
    public void setMaxFileSizeMB(Long maxFileSizeMB) { this.maxFileSizeMB = maxFileSizeMB; }

    public Integer getUploadedBooksCount() { return uploadedBooksCount; }
    public void setUploadedBooksCount(Integer uploadedBooksCount) { this.uploadedBooksCount = uploadedBooksCount; }

    public LocalDateTime getSubscriptionExpiryDate() { return subscriptionExpiryDate; }
    public void setSubscriptionExpiryDate(LocalDateTime subscriptionExpiryDate) { this.subscriptionExpiryDate = subscriptionExpiryDate; }

    public List<ChatHistory> getChatHistory() { return chatHistory; }
    public void setChatHistory(List<ChatHistory> chatHistory) { this.chatHistory = chatHistory; }

    public enum UserStatus {
        ACTIVE, BLOCKED, DELETED
    }

    public enum SubscriptionTier {
        FREE(5, 100L),
        PREMIUM(25, 500L),
        BUSINESS(100, 1000L);

        private final int maxBooks;
        private final long maxFileSizeMB;

        SubscriptionTier(int maxBooks, long maxFileSizeMB) {
            this.maxBooks = maxBooks;
            this.maxFileSizeMB = maxFileSizeMB;
        }

        public int getMaxBooks() { return maxBooks; }
        public long getMaxFileSizeMB() { return maxFileSizeMB; }
    }
}