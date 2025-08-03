package com.example.studentbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * Модель метаданных книги
 */
@Entity
@Table(name = "book_metadata")
public class BookMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String title;
    
    private String author;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @NotBlank
    @Column(nullable = false, unique = true)
    private String fileName;
    
    @NotBlank
    @Column(nullable = false)
    private String minioPath;
    
    private String fileType;
    
    private Long fileSize;
    
    @Column(nullable = false)
    private LocalDateTime uploadDate;
    
    private LocalDateTime lastAccessDate;
    
    @Column(nullable = false)
    private Long uploadedBy;
    
    @Enumerated(EnumType.STRING)
    private BookStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String tags;
    
    // Конструкторы
    public BookMetadata() {
        this.uploadDate = LocalDateTime.now();
        this.status = BookStatus.ACTIVE;
    }
    
    public BookMetadata(String title, String author, String fileName, String minioPath, Long uploadedBy) {
        this();
        this.title = title;
        this.author = author;
        this.fileName = fileName;
        this.minioPath = minioPath;
        this.uploadedBy = uploadedBy;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getMinioPath() {
        return minioPath;
    }
    
    public void setMinioPath(String minioPath) {
        this.minioPath = minioPath;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public LocalDateTime getUploadDate() {
        return uploadDate;
    }
    
    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }
    
    public LocalDateTime getLastAccessDate() {
        return lastAccessDate;
    }
    
    public void setLastAccessDate(LocalDateTime lastAccessDate) {
        this.lastAccessDate = lastAccessDate;
    }
    
    public Long getUploadedBy() {
        return uploadedBy;
    }
    
    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
    
    public BookStatus getStatus() {
        return status;
    }
    
    public void setStatus(BookStatus status) {
        this.status = status;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public enum BookStatus {
        ACTIVE, ARCHIVED, DELETED
    }
}