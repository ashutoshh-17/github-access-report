package com.cloudeagle.accessreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "github.api")
public class GitHubProperties {
    private String baseUrl;
    private String token;
    private int pageSize = 100;
    private int collaboratorFetchConcurrency = 10;
    private int timeoutSeconds = 15;
}
