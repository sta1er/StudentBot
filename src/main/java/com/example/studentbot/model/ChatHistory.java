package com.example.studentbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * Модель истории чата пользователя с ботом
 */
@Entity
@Table(name = "chat_history")
public class ChatHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userMessage;
    
    @Column(columnDefinition = "TEXT")
    private String botResponse;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    private MessageType messageType;
    
    @Column(columnDefinition = "TEXT")
    private String context;
    
    private String relatedBookId;
    
    private Integer processingTimeMs;
    
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;
    
    // Конструкторы
    public ChatHistory() {
        this.timestamp = LocalDateTime.now();
        this.status = ProcessingStatus.COMPLETED;
    }
    
    public ChatHistory(User user, String userMessage, MessageType messageType) {
        this();
        this.user = user;
        this.userMessage = userMessage;
        this.messageType = messageType;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public String getUserMessage() {
        return userMessage;
    }
    
    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }
    
    public String getBotResponse() {
        return botResponse;
    }
    
    public void setBotResponse(String botResponse) {
        this.botResponse = botResponse;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
    
    public String getContext() {
        return context;
    }
    
    public void setContext(String context) {
        this.context = context;
    }
    
    public String getRelatedBookId() {
        return relatedBookId;
    }
    
    public void setRelatedBookId(String relatedBookId) {
        this.relatedBookId = relatedBookId;
    }
    
    public Integer getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(Integer processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public ProcessingStatus getStatus() {
        return status;
    }
    
    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }
    
    public enum MessageType {
        TEXT, COMMAND, DOCUMENT, PHOTO, AUDIO, VOICE, VIDEO
    }
    
    public enum ProcessingStatus {
        PROCESSING, COMPLETED, ERROR, TIMEOUT
    }
}