package com.cloudeagle.accessreport.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RepositoryAccess {
    private String repository;
    private String permission;
    private boolean isPrivate;
}
