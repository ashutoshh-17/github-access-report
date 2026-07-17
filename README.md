# GitHub Organization Access Report

A Spring Boot service that connects to a GitHub organization and generates a report
mapping **users to the repositories they have access to**, along with their permission
level on each repository.

Built for organizations at scale: **100+ repositories** and **1000+ users** with
repository access.

---

## 1. How to run the project

### Prerequisites
- Java 17+
- Maven 3.8+
- A GitHub Personal Access Token (see [Authentication](#2-how-authentication-is-configured))

### Steps

```bash
# 1. Clone the repo
git clone <this-repo-url>
cd github-access-report

# 2. Export your GitHub token
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 3. Run
mvn spring-boot:run
```

The service starts on `http://localhost:8080` by default (configurable via `server.port`
in `src/main/resources/application.yml`).

### Running tests
```bash
mvn test
```

---

## 2. How authentication is configured

The service authenticates to GitHub using a **Personal Access Token (PAT)**, sent as a
`Bearer` token on every request via the `Authorization` header.

- The token is **never hardcoded**. It's read at startup from the `GITHUB_TOKEN`
  environment variable and injected via `github.api.token` in `application.yml`:
  ```yaml
  github:
    api:
      token: ${GITHUB_TOKEN:}
  ```
- Required scopes for the token:
  - `read:org` — to list organization repositories
  - `repo` — to read collaborator/permission data, including on private repos
- See `.env.example` for the expected format.

**Why a PAT and not a GitHub App?**
For an assignment/demo of this scope, a PAT is the simplest secure option. For a real production deployment, a **GitHub App** would be the better long-term choice as it gets its own higher rate limit and scopes access more tightly per repository. The `GitHubClient`/`WebClientConfig` split in this codebase makes it easy to swap auth mechanisms in the future if needed.

---

## 3. How to call the API endpoint

### Generate an access report
```
GET /api/v1/orgs/{org}/access-report
```

Example:
```bash
curl http://localhost:8080/api/v1/orgs/org-name/access-report
```

Example response:
```json
{
  "organization": "org-name",
  "repositoryCount": 128,
  "userCount": 943,
  "generatedAt": "2026-07-16T10:15:30Z",
  "users": [
    {
      "username": "alice-dev",
      "repositoryCount": 3,
      "repositories": [
        { "repository": "billing-service", "permission": "admin", "isPrivate": true },
        { "repository": "frontend-app", "permission": "write", "isPrivate": true },
        { "repository": "docs-site", "permission": "read", "isPrivate": false }
      ]
    }
  ]
}
```

### API Response Screenshots

![Postman Output 1](assets/Output%201.png)

![Postman Output 2](assets/Output%202.png)

### Health check
```
GET /api/v1/health
```

---

## 4. Assumptions and design decisions

- **Why user → repos, not repo → users:** The task asks "which repos can each user access", so I made the response user-first. But GitHub has no single API for that — I still fetch repo-by-repo first (list repos, get collaborators for each), then flip it into a user-first shape before returning it.

- **Where permission comes from:** Using `GET /repos/{owner}/{repo}/collaborators?affiliation=all` so outside collaborators show up too, not just direct members. I check the `role_name` field first, and if it's missing, fall back to the `permissions` map (admin > maintain > write > triage > read).

- **Archived/disabled repos are skipped** — not useful for an active access report.

- **Concurrent calls, not a loop (the scale part):** Repo listing follows GitHub's `Link` header for pagination. For collaborators, looping one repo at a time would be too slow at 100+ repos, so I used WebClient + `flatMap` to fetch multiple repos in parallel, capped at 10 concurrent calls (`collaborator-fetch-concurrency`) to avoid hitting rate limits.

- **Rate limits:** On a 403/429, it retries with backoff (up to 3 times) instead of failing outright.

- **Caching:** Each org's report is cached for 5 minutes (Caffeine, in-memory) so repeated test calls don't burn rate limit. Would need Redis instead for multi-instance deployment.

- **Missing access:** If the token can't read a specific repo's collaborators, that repo is skipped with a logged warning instead of failing the whole report.

## 5. Future scope

- **GitHub Teams:** Access granted via team membership (not individual collaborators) isn't tracked yet — would need `GET /orgs/{org}/teams`.
- **GraphQL:** Could replace REST to cut down the number of requests.
- **Redis cache:** For scaling across multiple instances.

---

## 6. Project structure

```
src/main/java/com/cloudeagle/accessreport/
├── config/          WebClient + externalized GitHub API settings
├── client/           GitHubClient — pagination, rate-limit retry, raw API calls
├── model/            DTOs (both raw GitHub shapes and the report's response shapes)
├── service/          AccessReportService — concurrent fetch + user-pivot aggregation
├── controller/        REST endpoint
└── exception/        Centralized error handling
```
