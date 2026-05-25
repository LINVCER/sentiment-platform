package com.sentiment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sentiment.mapper")
@EnableScheduling
public class SentimentPlatform {
    public static void main(String[] args) {
        SpringApplication.run(SentimentPlatform.class, args);
    }
}
