FROM python:3.11-slim

LABEL maintainer="Student Bot Team"
LABEL description="AI Service for Student Helper Bot"

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Создание пользователя для приложения
RUN useradd --create-home --shell /bin/bash aiservice

# Установка рабочей директории
WORKDIR /app

# Копирование файлов зависимостей
COPY requirements.txt .

# Установка Python зависимостей
RUN pip install --no-cache-dir --upgrade -r requirements.txt

# Копирование исходного кода
COPY main.py .
COPY models/ ./models/

# Изменение владельца файлов
RUN chown -R aiservice:aiservice /app

# Переключение на пользователя приложения
USER aiservice

# Проверка здоровья
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl --fail http://localhost:8000/health || exit 1

# Открытие порта
EXPOSE 8000

# Запуск приложения
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]