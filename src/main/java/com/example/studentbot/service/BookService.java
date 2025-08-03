package com.example.studentbot.service;

import com.example.studentbot.model.BookMetadata;
import com.example.studentbot.repository.BookMetadataRepository;
import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.Optional;

@Service
public class BookService {
    private static final Logger logger = LoggerFactory.getLogger(BookService.class);

    private final BookMetadataRepository bookMetadataRepository;
    private final MinioClient minioClient;

    @Value("${minio.buckets.books:books}")
    private String booksBucket;

    public BookService(BookMetadataRepository bookMetadataRepository, MinioClient minioClient) {
        this.bookMetadataRepository = bookMetadataRepository;
        this.minioClient = minioClient;
    }

    /**
     * Получить список книг пользователя
     */
    public List<BookMetadata> getUserBooks(Long userId) {
        return bookMetadataRepository.findByUploadedBy(userId);
    }

    /**
     * Загрузить новый документ пользователя в MinIO
     */
    public void uploadDocument(String fileId, String fileName, Long userId,
                               InputStream fileStream, long fileSize, String contentType) {
        try {
            // Сохраняем в MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(booksBucket)
                            .object(fileName)
                            .stream(fileStream, fileSize, -1)
                            .contentType(contentType)
                            .build()
            );

            // Добавляем запись о файле в базу
            BookMetadata book = new BookMetadata();
            book.setTitle(extractTitleFromFileName(fileName));
            book.setFileName(fileName);
            book.setMinioPath(booksBucket + "/" + fileName);
            book.setFileType(contentType);
            book.setFileSize(fileSize);
            book.setUploadedBy(userId);

            bookMetadataRepository.save(book);
            logger.info("Документ {} успешно загружен пользователем {}", fileName, userId);

        } catch (Exception e) {
            logger.error("Ошибка загрузки документа {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Не удалось загрузить файл", e);
        }
    }

    /**
     * Получить файл книги как Resource для отправки в AI API
     */
    public Resource getBookFile(Long bookId) {
        Optional<BookMetadata> metadataOpt = bookMetadataRepository.findById(bookId);
        if (metadataOpt.isEmpty()) {
            throw new RuntimeException("Книга не найдена: " + bookId);
        }

        BookMetadata book = metadataOpt.get();
        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(booksBucket)
                            .object(book.getFileName())
                            .build()
            );

            return new InputStreamResource(inputStream);

        } catch (Exception e) {
            logger.error("Ошибка чтения файла '{}': {}", book.getFileName(), e.getMessage(), e);
            throw new RuntimeException("Ошибка при чтении файла книги", e);
        }
    }

    /**
     * Получить заголовок книги по её ID
     */
    public String getBookTitle(Long bookId) {
        return bookMetadataRepository.findById(bookId)
                .map(BookMetadata::getTitle)
                .orElse("Неизвестная книга");
    }

    /**
     * Извлечь название из имени файла
     */
    private String extractTitleFromFileName(String fileName) {
        if (fileName == null) return "Без названия";

        // Убираем расширение
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            fileName = fileName.substring(0, lastDotIndex);
        }

        // Заменяем подчеркивания и дефисы на пробелы
        return fileName.replace("_", " ").replace("-", " ");
    }

    /**
     * Получить метаданные книги
     */
    public Optional<BookMetadata> getBookMetadata(Long bookId) {
        return bookMetadataRepository.findById(bookId);
    }

    /**
     * Поиск книг пользователя по ключевым словам
     */
    public List<BookMetadata> searchUserBooks(Long userId, String keyword) {
        return bookMetadataRepository.findByUploadedBy(userId).stream()
                .filter(book -> book.getTitle().toLowerCase().contains(keyword.toLowerCase()) ||
                        (book.getDescription() != null &&
                                book.getDescription().toLowerCase().contains(keyword.toLowerCase())))
                .toList();
    }
}