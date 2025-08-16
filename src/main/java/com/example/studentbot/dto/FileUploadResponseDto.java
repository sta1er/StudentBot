package com.example.studentbot.dto;

/**
 * DTO для ответа на загрузку файла
 */
public class FileUploadResponseDto {
    private String message;
    private String filename;
    private Integer currentBooksCount;
    private Integer maxBooks;

    public FileUploadResponseDto(String message, String filename, Integer currentBooksCount, Integer maxBooks) {
        this.message = message;
        this.filename = filename;
        this.currentBooksCount = currentBooksCount;
        this.maxBooks = maxBooks;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Integer getCurrentBooksCount() {
        return currentBooksCount;
    }

    public void setCurrentBooksCount(Integer currentBooksCount) {
        this.currentBooksCount = currentBooksCount;
    }

    public Integer getMaxBooks() {
        return maxBooks;
    }

    public void setMaxBooks(Integer maxBooks) {
        this.maxBooks = maxBooks;
    }
}
