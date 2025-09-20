package com.example.studentbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StudentBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentBotApplication.class, args);
    }

}
