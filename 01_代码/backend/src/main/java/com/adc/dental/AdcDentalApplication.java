package com.adc.dental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdcDentalApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdcDentalApplication.class, args);
    }
}
