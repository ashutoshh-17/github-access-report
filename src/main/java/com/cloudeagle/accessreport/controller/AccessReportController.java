package com.cloudeagle.accessreport.controller;

import com.cloudeagle.accessreport.model.AccessReportResponse;
import com.cloudeagle.accessreport.service.AccessReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccessReportController {

    private final AccessReportService accessReportService;

    // GET /api/v1/orgs/{org}/access-report
    // Returns a JSON report mapping every user with access to the org's
    // repositories,
    // to the list of repositories they can access and their permission level on
    // each.
    @GetMapping("/api/v1/orgs/{org}/access-report")
    public ResponseEntity<AccessReportResponse> getAccessReport(@PathVariable String org) {
        return ResponseEntity.ok(accessReportService.buildReport(org));
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
