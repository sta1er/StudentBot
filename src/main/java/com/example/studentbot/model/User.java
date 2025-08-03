package com.example.studentbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Модель пользователя Telegram бота
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
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatHistory> chatHistory;
    
    // Конструкторы
    public User() {
        this.registrationDate = LocalDateTime.now();
        this.lastActivityDate = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
    }
    
    public User(Long telegramId, String firstName, String lastName, String username, String languageCode) {
        this();
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.languageCode = languageCode;
    }
    
    // Getters and Setters
    public Long getTelegramId() {
        return telegramId;
    }
    
    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getLanguageCode() {
        return languageCode;
    }
    
    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }
    
    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }
    
    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }
    
    public LocalDateTime getLastActivityDate() {
        return lastActivityDate;
    }
    
    public void setLastActivityDate(LocalDateTime lastActivityDate) {
        this.lastActivityDate = lastActivityDate;
    }
    
    public String getPreferences() {
        return preferences;
    }
    
    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }
    
    public UserStatus getStatus() {
        return status;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public List<ChatHistory> getChatHistory() {
        return chatHistory;
    }
    
    public void setChatHistory(List<ChatHistory> chatHistory) {
        this.chatHistory = chatHistory;
    }
    
    public enum UserStatus {
        ACTIVE, BLOCKED, DELETED
    }
}