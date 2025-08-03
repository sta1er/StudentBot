package com.example.studentbot.repository;

import com.example.studentbot.model.BookMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с метаданными книг
 */
@Repository
public interface BookMetadataRepository extends JpaRepository<BookMetadata, Long> {
    
    /**
     * Найти книгу по имени файла
     */
    Optional<BookMetadata> findByFileName(String fileName);
    
    /**
     * Найти книги по статусу
     */
    List<BookMetadata> findByStatus(BookMetadata.BookStatus status);
    
    /**
     * Найти книги по автору
     */
    List<BookMetadata> findByAuthorContainingIgnoreCase(String author);
    
    /**
     * Найти книги по названию
     */
    List<BookMetadata> findByTitleContainingIgnoreCase(String title);
    
    /**
     * Найти книги, загруженные пользователем
     */
    List<BookMetadata> findByUploadedBy(Long userId);
    
    /**
     * Найти книги по типу файла
     */
    List<BookMetadata> findByFileType(String fileType);
    
    /**
     * Найти книги, загруженные после определенной даты
     */
    List<BookMetadata> findByUploadDateAfter(LocalDateTime date);
    
    /**
     * Найти популярные книги (по последнему доступу)
     */
    @Query("SELECT b FROM BookMetadata b WHERE b.lastAccessDate IS NOT NULL " +
           "ORDER BY b.lastAccessDate DESC")
    List<BookMetadata> findRecentlyAccessedBooks();
    
    /**
     * Найти книги по тегам
     */
    @Query("SELECT b FROM BookMetadata b WHERE LOWER(b.tags) LIKE LOWER(CONCAT('%', :tag, '%'))")
    List<BookMetadata> findByTagsContaining(@Param("tag") String tag);
    
    /**
     * Поиск книг по ключевым словам (название, автор, описание, теги)
     */
    @Query("SELECT b FROM BookMetadata b WHERE " +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<BookMetadata> searchBooks(@Param("keyword") String keyword);
    
    /**
     * Подсчитать количество книг по статусу
     */
    Long countByStatus(BookMetadata.BookStatus status);
    
    /**
     * Найти самые большие файлы
     */
    @Query("SELECT b FROM BookMetadata b WHERE b.fileSize IS NOT NULL ORDER BY b.fileSize DESC")
    List<BookMetadata> findLargestFiles();
}