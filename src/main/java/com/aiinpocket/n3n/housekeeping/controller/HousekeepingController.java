package com.aiinpocket.n3n.housekeeping.controller;

import com.aiinpocket.n3n.housekeeping.entity.HousekeepingJob;
import com.aiinpocket.n3n.housekeeping.repository.HousekeepingJobRepository;
import com.aiinpocket.n3n.housekeeping.service.HousekeepingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for housekeeping operations.
 * Only accessible by admins.
 */
@RestController
@RequestMapping("/api/admin/housekeeping")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class HousekeepingController {

    private final HousekeepingService housekeepingService;
    private final HousekeepingJobRepository jobRepository;

    /**
     * Get housekeeping statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(housekeepingService.getStatistics());
    }

    /**
     * Manually trigger housekeeping cleanup.
     */
    @PostMapping("/run")
    public ResponseEntity<?> runCleanup() {
        log.info("HOUSEKEEPING_MANUAL triggered by admin");

        HousekeepingJob job = housekeepingService.runCleanup();

        if (job == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Housekeeping job already running",
                            "message", "Please wait for the current job to complete"
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus(),
                "recordsProcessed", job.getRecordsProcessed(),
                "recordsArchived", job.getRecordsArchived(),
                "recordsDeleted", job.getRecordsDeleted()
        ));
    }

    /**
     * Get housekeeping job history.
     */
    @GetMapping("/jobs")
    public ResponseEntity<Page<HousekeepingJob>> getJobHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size))
        );
    }

    /**
     * Get a specific job.
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<HousekeepingJob> getJob(@PathVariable java.util.UUID id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cleanup old history records manually.
     */
    @PostMapping("/cleanup-history")
    public ResponseEntity<Map<String, Object>> cleanupHistory() {
        log.info("HISTORY_CLEANUP_MANUAL triggered by admin");

        int deleted = housekeepingService.cleanupOldHistory();

        return ResponseEntity.ok(Map.of(
                "recordsDeleted", deleted,
                "message", "History cleanup completed"
        ));
    }
}
