package com.aiinpocket.n3n;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class N3nApplication {

    public static void main(String[] args) {
        SpringApplication.run(N3nApplication.class, args);
    }

}
