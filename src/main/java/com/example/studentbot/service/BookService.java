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
    public BookMetadata uploadDocument(String fileId, String fileName, Long userId,
                                       InputStream fileStream, long fileSize, String contentType) {
        try {
            // Генерируем уникальное имя файла для избежания конфликтов
            String uniqueFileName = generateUniqueFileName(fileName, userId);

            // Сохраняем в MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(booksBucket)
                            .object(uniqueFileName)
                            .stream(fileStream, fileSize, -1)
                            .contentType(contentType)
                            .build()
            );

            // Добавляем запись о файле в базу
            BookMetadata book = new BookMetadata();
            book.setTitle(extractTitleFromFileName(fileName));
            book.setFileName(uniqueFileName);
            book.setMinioPath(booksBucket + "/" + uniqueFileName);
            book.setFileType(contentType);
            book.setFileSize(fileSize);
            book.setUploadedBy(userId);

            BookMetadata savedBook = bookMetadataRepository.save(book);
            logger.info("Документ {} успешно загружен пользователем {}", fileName, userId);

            return savedBook;

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

            // Обновляем время последнего доступа
            book.setLastAccessDate(java.time.LocalDateTime.now());
            bookMetadataRepository.save(book);

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
     * Получить метаданные книги
     */
    public Optional<BookMetadata> getBookMetadata(Long bookId) {
        return bookMetadataRepository.findById(bookId);
    }

    /**
     * Удалить книгу
     */
    public void deleteBook(Long bookId) {
        Optional<BookMetadata> bookOpt = bookMetadataRepository.findById(bookId);
        if (bookOpt.isPresent()) {
            BookMetadata book = bookOpt.get();

            try {
                // Удаляем файл из MinIO
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(booksBucket)
                                .object(book.getFileName())
                                .build()
                );

                // Удаляем запись из базы данных
                bookMetadataRepository.delete(book);

                logger.info("Книга {} успешно удалена", book.getTitle());

            } catch (Exception e) {
                logger.error("Ошибка удаления книги {}: {}", book.getTitle(), e.getMessage(), e);
                throw new RuntimeException("Не удалось удалить книгу", e);
            }
        }
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

    /**
     * Получить общую статистику по книгам пользователя
     */
    public BookStats getUserBookStats(Long userId) {
        List<BookMetadata> books = getUserBooks(userId);

        long totalSize = books.stream()
                .mapToLong(book -> book.getFileSize() != null ? book.getFileSize() : 0L)
                .sum();

        return new BookStats(
                books.size(),
                totalSize,
                books.stream().mapToInt(book -> book.getTitle().length()).average().orElse(0.0)
        );
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
        return fileName.replace("_", " ").replace("-", " ").trim();
    }

    /**
     * Генерируем уникальное имя файла
     */
    private String generateUniqueFileName(String originalFileName, Long userId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = "";

        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFileName.substring(lastDotIndex);
        }

        return String.format("user_%d_%s_%s%s", userId, timestamp,
                originalFileName.replaceAll("[^a-zA-Z0-9.]", "_"), extension);
    }

    /**
     * Класс для статистики книг
     */
    public static class BookStats {
        private final int totalBooks;
        private final long totalSizeBytes;
        private final double averageTitleLength;

        public BookStats(int totalBooks, long totalSizeBytes, double averageTitleLength) {
            this.totalBooks = totalBooks;
            this.totalSizeBytes = totalSizeBytes;
            this.averageTitleLength = averageTitleLength;
        }

        public int getTotalBooks() { return totalBooks; }
        public long getTotalSizeBytes() { return totalSizeBytes; }
        public double getAverageTitleLength() { return averageTitleLength; }

        public String getTotalSizeFormatted() {
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.1f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024));
            return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024 * 1024));
        }
    }
}