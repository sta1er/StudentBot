# Dockerfile с поддержкой Mini App
FROM openjdk:17-jdk-slim

LABEL maintainer="Student Bot Team"
LABEL description="Student Helper Telegram Bot with Mini App Support"

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    nginx \
    && rm -rf /var/lib/apt/lists/*

# Создание пользователя для приложения
RUN useradd --create-home --shell /bin/bash student-bot

# Создание директорий
RUN mkdir -p /app/logs /app/miniapp /etc/nginx/sites-available /etc/nginx/sites-enabled && \
    chown -R student-bot:student-bot /app

# Настройка Nginx для Mini App
COPY nginx.conf /etc/nginx/sites-available/default
RUN ln -sf /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default

# Установка рабочей директории
WORKDIR /app

# Аргументы сборки
ARG JAR_FILE=build/libs/*.jar

# Копирование JAR файла и Mini App файлов
COPY ${JAR_FILE} app.jar
COPY miniapp/ /app/miniapp/

# Изменение владельца файлов
RUN chown -R student-bot:student-bot /app

# Переключение на пользователя приложения
USER student-bot

# Проверка здоровья
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Открытие портов
EXPOSE 8080 80

# Параметры JVM
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Создание стартового скрипта
RUN echo '#!/bin/bash\n\
set -e\n\
echo "Starting Nginx..."\n\
sudo nginx -g "daemon off;" &\n\
echo "Starting Spring Boot application..."\n\
java $JAVA_OPTS -jar app.jar\n\
wait' > start.sh && chmod +x start.sh

# Запуск приложения
ENTRYPOINT ["./start.sh"]