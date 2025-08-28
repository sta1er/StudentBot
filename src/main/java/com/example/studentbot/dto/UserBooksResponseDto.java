package com.example.studentbot.dto;

import com.example.studentbot.model.BookMetadata;
import java.util.List;

/**
 * DTO для ответа со списком книг пользователя
 */
public class UserBooksResponseDto {
    private List<BookMetadata> books;

    public UserBooksResponseDto(List<BookMetadata> books) {
        this.books = books;
    }

    // Getters and Setters
    public List<BookMetadata> getBooks() {
        return books;
    }

    public void setBooks(List<BookMetadata> books) {
        this.books = books;
    }
}