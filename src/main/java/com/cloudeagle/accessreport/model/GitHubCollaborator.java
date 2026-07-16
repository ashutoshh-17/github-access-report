package com.cloudeagle.accessreport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCollaborator {
    private String login;
    private long id;

    @JsonProperty("permissions")
    private Map<String, Boolean> permissions;

    @JsonProperty("role_name")
    private String roleName;

    public String highestPermission() {
        if (roleName != null && !roleName.isBlank()) {
            return roleName;
        }
        if (permissions == null)
            return "read";
        if (Boolean.TRUE.equals(permissions.get("admin")))
            return "admin";
        if (Boolean.TRUE.equals(permissions.get("maintain")))
            return "maintain";
        if (Boolean.TRUE.equals(permissions.get("push")))
            return "write";
        if (Boolean.TRUE.equals(permissions.get("triage")))
            return "triage";
        return "read";
    }
}
