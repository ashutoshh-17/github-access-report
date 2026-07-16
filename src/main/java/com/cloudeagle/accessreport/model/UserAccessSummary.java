package com.cloudeagle.accessreport.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAccessSummary {
    private String username;
    private int repositoryCount;
    private List<RepositoryAccess> repositories;
}
