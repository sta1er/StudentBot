plugins {
    java
    id("org.springframework.boot") version "3.1.1"
    id("io.spring.dependency-management") version "1.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Database
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("io.minio:minio:8.5.4")

    // Telegram
    implementation("org.telegram:telegrambots:6.8.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // HTTP клиент для REST API
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // JSON processing (уже включено в spring-boot-starter-web, но явно указываем)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")

    // Apache PDFBox для PDF документов
    implementation("org.apache.pdfbox:pdfbox:2.0.29")

    // Apache POI для DOCX документов
    implementation("org.apache.poi:poi:5.2.4")
    implementation("org.apache.poi:poi-ooxml:5.2.4")
    implementation("org.apache.poi:poi-scratchpad:5.2.4")

    // Apache Commons IO для вспомогательных классов
    implementation("commons-io:commons-io:2.15.1")

    // Async processing
    implementation("org.springframework.boot:spring-boot-starter-async")
}

tasks.withType<Test> {
    useJUnitPlatform()
}