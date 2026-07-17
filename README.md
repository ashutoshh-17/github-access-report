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

- **Aggregation direction (user → repos, not repo → users):** The assignment asks for
  "which repositories each user has access to," so the top-level response is a list of
  users, each carrying their list of accessible repositories and permission level. The
  underlying data is fetched repo-first (that's how GitHub's API is shaped — there's no
  "list all org members with all their repo access" endpoint) and then pivoted into the
  user-first view server-side.

- **Permission source:** GitHub's collaborators endpoint
  (`GET /repos/{owner}/{repo}/collaborators?affiliation=all`) is used with
  `affiliation=all` so both directly-added collaborators and outside collaborators are
  captured, not just direct members. The highest permission level per user/repo pair is
  derived from the `role_name` field when present, falling back to the `permissions` map
  (`admin` > `maintain` > `push`/write > `triage` > `read`).

- **Archived/disabled repos are excluded** from the report by default, since they're
  typically not relevant to an active access-governance view.

- **Concurrency, not sequential calls (the scale requirement):** Repository listing is
  paginated automatically (following GitHub's `Link` header `rel="next"`). Once the repo
  list is known, collaborator lookups for each repo are fanned out **concurrently**
  using a reactive `WebClient` + `Flux.flatMap(..., concurrency)`, bounded by
  `github.api.collaborator-fetch-concurrency` (default 10) so a 100+ repo org doesn't
  fire 100+ requests all at once and blow through GitHub's rate limit, but also doesn't
  wait on them one at a time.

- **Rate limit handling:** If GitHub responds with `403`/`429` due to rate limiting,
  the client retries with exponential backoff (up to 3 attempts) rather than failing the
  whole report on a transient limit hit.

- **Caching:** Report results are cached in-memory for 5 minutes (Caffeine,
  `spring.cache`) per organization, since access data doesn't change second-to-second
  and repeated demo/testing calls shouldn't burn API quota unnecessarily. This is a
  simple in-memory cache appropriate for a single-instance deployment; a multi-instance
  production deployment would swap this for a shared cache (e.g. Redis).

- **Error handling & Missing Access:** A `GlobalExceptionHandler` maps global GitHub API failures to clean JSON error responses. Crucially, **if the token does not have access to a specific repository's collaborators URL**, the application does not crash or fail the entire request. Instead, it catches the 403/404 error, logs a graceful warning, and simply skips that repository. This ensures that the report generation continues uninterrupted for the repositories the user *does* have access to.

## 5. Future Scope / TODOs

- **Team-based Access:** Currently, GitHub Teams that grant repo access without an individual collaborator entry are not fully tracked. Calling `GET /orgs/{org}/teams` would fix this.
- **GraphQL Migration:** We could explore migrating from REST API v3 to GraphQL to drastically reduce the number of HTTP requests for fetching collaborators.
- **Shared Caching:** Swap the in-memory Caffeine cache with Redis for a multi-instance production environment.

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
