package com.example.studentbot.service;

import com.example.studentbot.model.BookMetadata;
import com.example.studentbot.repository.BookMetadataRepository;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /** Получить список книг пользователя */
    public List<BookMetadata> getUserBooks(Long userId) {
        return bookMetadataRepository.findByUploadedBy(userId);
    }

    /** Загрузить новый документ пользователя в MinIO */
    public void uploadDocument(String fileId, String fileName, Long userId, InputStream fileStream, long fileSize, String contentType) {
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
            book.setTitle(fileName); // Можно добавить парсер названия и автора
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

    /** Прочитать текстовое содержимое книги из MinIO */
    public String getBookContent(Long bookId) {
        Optional<BookMetadata> metadataOpt = bookMetadataRepository.findById(bookId);
        if (metadataOpt.isEmpty()) return null;

        BookMetadata book = metadataOpt.get();
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(booksBucket)
                        .object(book.getFileName())
                        .build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            logger.error("Ошибка чтения файла '{}': {}", book.getFileName(), e.getMessage(), e);
            return "Ошибка при чтении содержимого книги.";
        }
    }

    /** Получить заголовок книги по её ID */
    public String getBookTitle(Long bookId) {
        return bookMetadataRepository.findById(bookId)
                .map(BookMetadata::getTitle)
                .orElse("Неизвестная книга");
    }
}
