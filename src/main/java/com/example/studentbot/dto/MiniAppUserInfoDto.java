package com.example.studentbot.dto;

/**
 * DTO для информации о пользователе в Mini App
 */
public class MiniAppUserInfoDto {
    private Long telegramId;
    private String firstName;
    private String subscriptionTier;
    private Integer uploadedBooksCount;
    private Integer maxBooks;
    private Long maxFileSizeMB;

    public MiniAppUserInfoDto(Long telegramId, String firstName, String subscriptionTier, 
                             Integer uploadedBooksCount, Integer maxBooks, Long maxFileSizeMB) {
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.subscriptionTier = subscriptionTier;
        this.uploadedBooksCount = uploadedBooksCount;
        this.maxBooks = maxBooks;
        this.maxFileSizeMB = maxFileSizeMB;
    }

    // Getters and Setters
    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(String subscriptionTier) { this.subscriptionTier = subscriptionTier; }

    public Integer getUploadedBooksCount() { return uploadedBooksCount; }
    public void setUploadedBooksCount(Integer uploadedBooksCount) { this.uploadedBooksCount = uploadedBooksCount; }

    public Integer getMaxBooks() { return maxBooks; }
    public void setMaxBooks(Integer maxBooks) { this.maxBooks = maxBooks; }

    public Long getMaxFileSizeMB() { return maxFileSizeMB; }
    public void setMaxFileSizeMB(Long maxFileSizeMB) { this.maxFileSizeMB = maxFileSizeMB; }
}

