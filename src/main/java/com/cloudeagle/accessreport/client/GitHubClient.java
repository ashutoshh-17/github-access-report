package com.cloudeagle.accessreport.client;

import com.cloudeagle.accessreport.config.GitHubProperties;
import com.cloudeagle.accessreport.exception.GitHubApiException;
import com.cloudeagle.accessreport.model.GitHubCollaborator;
import com.cloudeagle.accessreport.model.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Thin wrapper around the GitHub REST API v3.
// Handles pagination and basic rate limiting automatically.
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubClient {

    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final WebClient gitHubWebClient;
    private final GitHubProperties properties;

    // Fetches every repository belonging to the given organization, following
    // pagination.
    public Flux<GitHubRepository> fetchOrgRepositories(String org) {
        String initialUri = String.format("/orgs/%s/repos?per_page=%d&page=1&type=all",
                org, properties.getPageSize());
        return paginate(initialUri, new ParameterizedTypeReference<List<GitHubRepository>>() {
        });
    }

    // Fetches every collaborator (direct + outside) with access to a specific
    // repository,
    // following pagination. `affiliation=all` ensures org owners/admins with
    // implicit
    // access are included, not just directly-added collaborators.
    public Flux<GitHubCollaborator> fetchRepositoryCollaborators(String org, String repoName) {
        String initialUri = String.format("/repos/%s/%s/collaborators?per_page=%d&page=1&affiliation=all",
                org, repoName, properties.getPageSize());
        return paginate(initialUri, new ParameterizedTypeReference<List<GitHubCollaborator>>() {
        })
                // A repo with branch protection / visibility restrictions can 403 on the
                // collaborators endpoint even though repo listing succeeded. Skip it rather
                // than failing the whole report, but log so it's visible.
                .onErrorResume(GitHubApiException.class, ex -> {
                    log.warn("Skipping collaborators for repo '{}': {}", repoName, ex.getMessage());
                    return Flux.empty();
                });
    }

    // Generic pagination helper: fetches the given URI, then keeps following the
    // "next" Link header until GitHub stops returning one.
    private <T> Flux<T> paginate(String uri, ParameterizedTypeReference<List<T>> typeRef) {
        return fetchPage(uri, typeRef)
                .expand(page -> page.nextUri() != null ? fetchPage(page.nextUri(), typeRef) : Mono.empty())
                .flatMapIterable(Page::items);
    }

    private <T> Mono<Page<T>> fetchPage(String uri, ParameterizedTypeReference<List<T>> typeRef) {
        return gitHubWebClient.get()
                .uri(uri)
                .retrieve()
                .toEntity(typeRef)
                .map(response -> new Page<>(
                        response.getBody() != null ? response.getBody() : List.of(),
                        extractNextLink(response)))
                .retryWhen(rateLimitRetrySpec())
                .onErrorMap(WebClientResponseException.class, this::toGitHubApiException);
    }

    private String extractNextLink(ResponseEntity<?> response) {
        String linkHeader = response.getHeaders().getFirst(HttpHeaders.LINK);
        if (linkHeader == null)
            return null;
        Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            String fullUrl = matcher.group(1);
            // Strip the base URL since WebClient is already configured with it.
            return fullUrl.replace(properties.getBaseUrl(), "");
        }
        return null;
    }

    // Retries on secondary rate limits / abuse detection (HTTP 403 with a
    // Retry-After header)
    // and on primary rate limit exhaustion (403 with X-RateLimit-Remaining: 0),
    // waiting until
    // the window resets. Regular 4xx errors (bad org name, 404, permission errors)
    // are not retried.
    private Retry rateLimitRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .filter(this::isRetryableRateLimitError)
                .doBeforeRetry(signal -> log.warn("Rate limited by GitHub, retrying (attempt {})",
                        signal.totalRetries() + 1));
    }

    private boolean isRetryableRateLimitError(Throwable throwable) {
        if (!(throwable instanceof WebClientResponseException wcre))
            return false;
        HttpStatusCode status = wcre.getStatusCode();
        if (status.value() == 429)
            return true;
        if (status.value() == 403) {
            String remaining = wcre.getHeaders().getFirst("X-RateLimit-Remaining");
            return "0".equals(remaining);
        }
        return false;
    }

    private GitHubApiException toGitHubApiException(WebClientResponseException ex) {
        String message = String.format("GitHub API call failed with status %s: %s",
                ex.getStatusCode(), ex.getResponseBodyAsString());
        return new GitHubApiException(message, ex.getStatusCode());
    }

    private record Page<T>(List<T> items, String nextUri) {
    }
}
