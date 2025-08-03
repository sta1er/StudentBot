FROM openjdk:17-jdk-slim

LABEL maintainer="Student Bot Team"
LABEL description="Student Helper Telegram Bot with Local AI"

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Создание пользователя для приложения
RUN useradd --create-home --shell /bin/bash student-bot

# Создание директорий
RUN mkdir -p /app/logs && \
    chown -R student-bot:student-bot /app

# Установка рабочей директории
WORKDIR /app

# Аргументы сборки
ARG JAR_FILE=build/libs/*.jar

# Копирование JAR файла
COPY ${JAR_FILE} app.jar

# Изменение владельца файлов
RUN chown student-bot:student-bot app.jar

# Переключение на пользователя приложения
USER student-bot

# Проверка здоровья
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Открытие порта
EXPOSE 8080

# Параметры JVM
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]