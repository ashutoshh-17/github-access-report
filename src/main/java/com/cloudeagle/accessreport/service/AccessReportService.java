package com.cloudeagle.accessreport.service;

import com.cloudeagle.accessreport.client.GitHubClient;
import com.cloudeagle.accessreport.config.GitHubProperties;
import com.cloudeagle.accessreport.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessReportService {

        private final GitHubClient gitHubClient;
        private final GitHubProperties properties;

        // Builds the full access report for an organization.
        // Fetches repositories and then fetches collaborators for each repo
        // concurrently.
        // Results are cached to avoid hitting GitHub API limits repeatedly during
        // testing.
        @Cacheable("accessReport")
        public AccessReportResponse buildReport(String org) {
                if (org == null || org.isBlank()) {
                        throw new IllegalArgumentException("Organization name must not be empty");
                }

                List<GitHubRepository> allRepos = gitHubClient.fetchOrgRepositories(org).collectList().block();
                List<GitHubRepository> repos = new java.util.ArrayList<>();
                if (allRepos != null) {
                        for (GitHubRepository repo : allRepos) {
                                if (!repo.isArchived() && !repo.isDisabled()) {
                                        repos.add(repo);
                                }
                        }
                }

                if (repos.isEmpty()) {
                        return new AccessReportResponse(org, 0, 0, Instant.now(), List.of());
                }

                // username -> list of (repo, permission) pairs, built up concurrently and
                // safely
                // since multiple reactive threads write to it during the flatMap fan-out below.
                Map<String, List<RepositoryAccess>> userToRepos = new ConcurrentHashMap<>();

                Flux.fromIterable(repos)
                                .flatMap(repo -> gitHubClient.fetchRepositoryCollaborators(org, repo.getName())
                                                .doOnNext(collaborator -> {
                                                        String username = collaborator.getLogin();
                                                        if (!userToRepos.containsKey(username)) {
                                                                userToRepos.put(username, new CopyOnWriteArrayList<>());
                                                        }
                                                        RepositoryAccess access = new RepositoryAccess(
                                                                        repo.getName(),
                                                                        collaborator.highestPermission(),
                                                                        repo.isPrivate());
                                                        userToRepos.get(username).add(access);
                                                }),
                                                properties.getCollaboratorFetchConcurrency())
                                .then()
                                .block();

                List<UserAccessSummary> users = new java.util.ArrayList<>();
                for (Map.Entry<String, List<RepositoryAccess>> entry : userToRepos.entrySet()) {
                        String username = entry.getKey();
                        List<RepositoryAccess> repoAccessList = entry.getValue();
                        users.add(new UserAccessSummary(username, repoAccessList.size(), repoAccessList));
                }

                users.sort(new Comparator<UserAccessSummary>() {
                        @Override
                        public int compare(UserAccessSummary u1, UserAccessSummary u2) {
                                return u1.getUsername().compareToIgnoreCase(u2.getUsername());
                        }
                });

                log.info("Access report for org '{}': {} repos scanned, {} users with access",
                                org, repos.size(), users.size());

                return new AccessReportResponse(org, repos.size(), users.size(), Instant.now(), users);
        }
}
