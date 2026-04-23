package com.devmatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.devmatch.config")
public class DevMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevMatchApplication.class, args);
    }
}
