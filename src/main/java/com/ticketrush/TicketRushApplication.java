package com.ticketrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class TicketRushApplication {

    public static void main(String[] args) {
        // Enforce UTC across the entire application JVM
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TicketRushApplication.class, args);
    }
}
