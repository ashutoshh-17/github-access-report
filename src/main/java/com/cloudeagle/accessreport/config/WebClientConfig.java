package com.cloudeagle.accessreport.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final GitHubProperties properties;

    @Bean
    public WebClient gitHubWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getTimeoutSeconds() * 1000)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.getTimeoutSeconds(), TimeUnit.SECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                // 5MB buffer - org listings with 1000+ collaborators can produce sizeable JSON payloads
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024));

        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken());
        }

        return builder.build();
    }
}
