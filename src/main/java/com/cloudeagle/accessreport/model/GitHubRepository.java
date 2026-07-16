package com.cloudeagle.accessreport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// Raw shape of a repository object as returned by GET /orgs/{org}/repos.
// Only fields relevant to the access report are mapped; everything else is ignored.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepository {
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private boolean archived;
    private boolean disabled;

    @JsonProperty("private")
    private boolean isPrivate;

    private String visibility;
}
