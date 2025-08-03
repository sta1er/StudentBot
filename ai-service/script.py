# Создам структуру файлов для проекта Telegram-бота

# Начнем с создания основных файлов Maven проекта
project_structure = """
student-helper-bot/
├── docker-compose.yml
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── studentbot/
│       │           ├── StudentBotApplication.java
│       │           ├── config/
│       │           │   ├── TelegramBotConfig.java
│       │           │   ├── DatabaseConfig.java
│       │           │   └── MinioConfig.java
│       │           ├── model/
│       │           │   ├── User.java
│       │           │   ├── BookMetadata.java
│       │           │   └── ChatHistory.java
│       │           ├── repository/
│       │           │   ├── UserRepository.java
│       │           │   ├── BookMetadataRepository.java
│       │           │   └── ChatHistoryRepository.java
│       │           ├── service/
│       │           │   ├── TelegramBotService.java
│       │           │   ├── AIService.java
│       │           │   ├── BookService.java
│       │           │   └── UserService.java
│       │           ├── controller/
│       │           │   └── AIController.java
│       │           └── dto/
│       │               ├── AIRequest.java
│       │               └── AIResponse.java
│       └── resources/
│           ├── application.yml
│           └── schema.sql
├── ai-service/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── main.py
│   └── models/
└── README.md
"""

print("Структура проекта:")
print(project_structure)