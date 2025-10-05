package com.example.studentbot.utils;

import java.util.ArrayList;
import java.util.List;

public class TelegramMessageUtils {

    // Максимальная длина сообщения в Telegram
    public static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    /**
     * Разбивает длинный текст на части, сохраняя целостность кодовых блоков
     * @param text исходный текст
     * @param maxLength максимальная длина одной части (по умолчанию TELEGRAM_MESSAGE_LIMIT)
     * @return список частей текста
     */
    public static List<String> splitTextPreservingCodeBlocks(String text, int maxLength) {
        List<String> parts = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return parts;
        }

        // Если текст короче лимита, возвращаем как есть
        if (text.length() <= maxLength) {
            parts.add(text);
            return parts;
        }

        String[] lines = text.split("\\n", -1); // -1 чтобы сохранить пустые строки
        StringBuilder currentPart = new StringBuilder();
        boolean insideCodeBlock = false;
        String codeBlockType = null; // тип кодового блока (java, python, etc.)

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isCodeBlockDelimiter = line.trim().startsWith("```");

            // Проверяем, поместится ли текущая строка
            int estimatedLength = currentPart.length() + line.length() + 1; // +1 для \n

            // Если не поместится и мы не внутри кодового блока, завершаем текущую часть
            if (estimatedLength > maxLength && !insideCodeBlock && currentPart.length() > 0) {
                parts.add(currentPart.toString().trim());
                currentPart = new StringBuilder();
            }

            // Если строка слишком длинная даже одна, то разбиваем её принудительно
            if (line.length() > maxLength && !isCodeBlockDelimiter) {
                // Добавляем текущую часть, если она не пустая
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString().trim());
                    currentPart = new StringBuilder();
                }

                // Разбиваем длинную строку на части
                List<String> lineParts = splitLongLine(line, maxLength);
                for (int j = 0; j < lineParts.size() - 1; j++) {
                    parts.add(lineParts.get(j));
                }
                // Последнюю часть добавляем в currentPart
                currentPart.append(lineParts.get(lineParts.size() - 1));
            } else {
                // Обычная строка - просто добавляем
                if (currentPart.length() > 0) {
                    currentPart.append("\n");
                }
                currentPart.append(line);
            }

            // Обрабатываем начало/конец кодового блока
            if (isCodeBlockDelimiter) {
                if (!insideCodeBlock) {
                    // Начинаем кодовый блок
                    insideCodeBlock = true;
                    // Извлекаем тип языка если есть (например, ```java)
                    String trimmedLine = line.trim();
                    if (trimmedLine.length() > 3) {
                        codeBlockType = trimmedLine.substring(3).trim();
                    }
                } else {
                    // Заканчиваем кодовый блок
                    insideCodeBlock = false;
                    codeBlockType = null;
                }
            }

            // Если достигли лимита внутри кодового блока, принудительно разбиваем
            if (currentPart.length() > maxLength && insideCodeBlock) {
                // Закрываем текущий блок
                currentPart.append("\n```");
                parts.add(currentPart.toString());

                // Начинаем новый блок с тем же типом языка
                currentPart = new StringBuilder();
                if (codeBlockType != null && !codeBlockType.isEmpty()) {
                    currentPart.append("```").append(codeBlockType).append("\n");
                } else {
                    currentPart.append("```\n");
                }
            }
        }

        // Добавляем оставшуюся часть
        if (currentPart.length() > 0) {
            String lastPart = currentPart.toString().trim();
            if (!lastPart.isEmpty()) {
                // Если мы всё ещё внутри кодового блока, закрываем его
                if (insideCodeBlock && !lastPart.endsWith("```")) {
                    lastPart += "\n```";
                }
                parts.add(lastPart);
            }
        }

        return parts;
    }

    /**
     * Разбивает длинную строку на части по словам или символам
     */
    private static List<String> splitLongLine(String line, int maxLength) {
        List<String> parts = new ArrayList<>();

        if (line.length() <= maxLength) {
            parts.add(line);
            return parts;
        }

        // Пытаемся разбить по словам
        String[] words = line.split(" ");
        StringBuilder currentPart = new StringBuilder();

        for (String word : words) {
            if (currentPart.length() + word.length() + 1 > maxLength) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart = new StringBuilder();
                }

                // Если одно слово слишком длинное, разбиваем принудительно
                if (word.length() > maxLength) {
                    while (word.length() > maxLength) {
                        parts.add(word.substring(0, maxLength));
                        word = word.substring(maxLength);
                    }
                    if (!word.isEmpty()) {
                        currentPart.append(word);
                    }
                } else {
                    currentPart.append(word);
                }
            } else {
                if (currentPart.length() > 0) {
                    currentPart.append(" ");
                }
                currentPart.append(word);
            }
        }

        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }

        return parts;
    }

    /**
     * Перегруженный метод с лимитом по умолчанию
     */
    public static List<String> splitTextPreservingCodeBlocks(String text) {
        return splitTextPreservingCodeBlocks(text, TELEGRAM_MESSAGE_LIMIT);
    }
}