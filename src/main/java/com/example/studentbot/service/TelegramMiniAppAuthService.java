package com.example.studentbot.service;

import com.example.studentbot.dto.TelegramUserInfoDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для аутентификации пользователей Telegram Mini App
 */
@Service
public class TelegramMiniAppAuthService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramMiniAppAuthService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";
    private static final int AUTH_TIMEOUT_SECONDS = 86400; // 24 часа

    @Value("${telegram.bot.token}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Валидация данных от Telegram Mini App
     */
    public boolean validateTelegramData(String initData) {
        try {
            // Парсим параметры
            Map<String, String> params = Arrays.stream(initData.split("&"))
                    .map(param -> param.split("=", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    ));

            String hash = params.remove("hash");
            if (hash == null) {
                logger.warn("Отсутствует hash в init data");
                return false;
            }

            // Проверяем время
            String authDateStr = params.get("auth_date");
            if (authDateStr != null) {
                long authDate = Long.parseLong(authDateStr);
                long now = Instant.now().getEpochSecond();

                if (now - authDate > AUTH_TIMEOUT_SECONDS) {
                    logger.warn("Init data устарел: auth_date = {}, now = {}", authDate, now);
                    return false;
                }
            }

            // Создаем строку для проверки
            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            // Вычисляем HMAC
            String calculatedHash = calculateHMAC(dataCheckString);

            boolean isValid = calculatedHash.equals(hash);

            if (!isValid) {
                logger.warn("Неверный hash. Ожидался: {}, получен: {}", calculatedHash, hash);
            }

            return isValid;

        } catch (Exception e) {
            logger.error("Ошибка при валидации Telegram data: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Извлечение информации о пользователе из init data
     */
    public TelegramUserInfoDto extractUserInfo(String initData) throws Exception {
        Map<String, String> params = Arrays.stream(initData.split("&"))
                .map(param -> param.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                ));

        String userJson = params.get("user");
        if (userJson == null) {
            throw new IllegalArgumentException("User data not found in init data");
        }

        JsonNode userNode = objectMapper.readTree(userJson);

        return new TelegramUserInfoDto(
                userNode.get("id").asLong(),
                userNode.get("first_name").asText(),
                userNode.has("last_name") ? userNode.get("last_name").asText() : null,
                userNode.has("username") ? userNode.get("username").asText() : null,
                userNode.has("language_code") ? userNode.get("language_code").asText() : "en"
        );
    }

    private String calculateHMAC(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        // Создаем secret key
        Mac hmacSha256 = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec webAppDataKey = new SecretKeySpec(WEB_APP_DATA.getBytes(), HMAC_SHA256);
        hmacSha256.init(webAppDataKey);
        byte[] secretKey = hmacSha256.doFinal(botToken.getBytes());

        // Вычисляем hash
        Mac hmacSha256Final = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec finalKey = new SecretKeySpec(secretKey, HMAC_SHA256);
        hmacSha256Final.init(finalKey);
        byte[] hash = hmacSha256Final.doFinal(data.getBytes());

        // Конвертируем в hex
        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}