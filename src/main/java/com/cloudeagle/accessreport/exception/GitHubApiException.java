package com.cloudeagle.accessreport.exception;

import org.springframework.http.HttpStatusCode;

public class GitHubApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public GitHubApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
