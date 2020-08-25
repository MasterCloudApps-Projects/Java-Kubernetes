package com.javieraviles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAutoConfiguration
public class ReloadConfigmapApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReloadConfigmapApplication.class, args);
    }
}
