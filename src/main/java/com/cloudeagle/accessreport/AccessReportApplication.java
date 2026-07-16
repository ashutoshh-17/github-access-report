package com.cloudeagle.accessreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AccessReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessReportApplication.class, args);
    }
}
