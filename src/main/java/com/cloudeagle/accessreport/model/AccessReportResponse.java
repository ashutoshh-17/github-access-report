package com.cloudeagle.accessreport.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccessReportResponse {
    private String organization;
    private int repositoryCount;
    private int userCount;
    private Instant generatedAt;
    private List<UserAccessSummary> users;
}
