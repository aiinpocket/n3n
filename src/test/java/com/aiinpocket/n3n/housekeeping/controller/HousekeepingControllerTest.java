package com.aiinpocket.n3n.housekeeping.controller;

import com.aiinpocket.n3n.housekeeping.entity.HousekeepingJob;
import com.aiinpocket.n3n.housekeeping.repository.HousekeepingJobRepository;
import com.aiinpocket.n3n.housekeeping.service.HousekeepingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HousekeepingControllerTest {

    @Mock
    private HousekeepingService housekeepingService;

    @Mock
    private HousekeepingJobRepository jobRepository;

    @InjectMocks
    private HousekeepingController housekeepingController;

    private HousekeepingJob sampleJob() {
        return HousekeepingJob.builder()
                .id(UUID.randomUUID())
                .jobType("execution_cleanup")
                .startedAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now())
                .status("completed")
                .recordsProcessed(100)
                .recordsArchived(80)
                .recordsDeleted(20)
                .details(Map.of("retentionDays", 30, "archiveToHistory", true, "batchSize", 500))
                .build();
    }

    private HousekeepingJob runningJob() {
        return HousekeepingJob.builder()
                .id(UUID.randomUUID())
                .jobType("execution_cleanup")
                .startedAt(Instant.now())
                .status("running")
                .recordsProcessed(50)
                .recordsArchived(30)
                .recordsDeleted(20)
                .build();
    }

    // ===== getStatistics (GET /api/admin/housekeeping/stats) =====

    @Test
    void getStatistics_returnsOkWithStats() {
        Map<String, Object> stats = Map.of(
                "totalExecutions", 500L,
                "archivedExecutions", 200L,
                "oldExecutions", 50L,
                "retentionDays", 30
        );
        when(housekeepingService.getStatistics()).thenReturn(stats);

        var result = housekeepingController.getStatistics();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("totalExecutions", 500L);
        assertThat(result.getBody()).containsEntry("archivedExecutions", 200L);
        assertThat(result.getBody()).containsEntry("oldExecutions", 50L);
        assertThat(result.getBody()).containsEntry("retentionDays", 30);
        verify(housekeepingService).getStatistics();
    }

    @Test
    void getStatistics_emptyStats_returnsOkWithEmptyMap() {
        when(housekeepingService.getStatistics()).thenReturn(Map.of());

        var result = housekeepingController.getStatistics();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getStatistics_serviceThrows_propagatesException() {
        when(housekeepingService.getStatistics()).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> housekeepingController.getStatistics())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");
    }

    // ===== runCleanup (POST /api/admin/housekeeping/run) =====

    @Test
    void runCleanup_success_returnsOkWithJobDetails() {
        var job = sampleJob();
        when(housekeepingService.runCleanup()).thenReturn(job);

        var result = housekeepingController.runCleanup();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("jobId", job.getId());
        assertThat(body).containsEntry("status", "completed");
        assertThat(body).containsEntry("recordsProcessed", 100);
        assertThat(body).containsEntry("recordsArchived", 80);
        assertThat(body).containsEntry("recordsDeleted", 20);
        verify(housekeepingService).runCleanup();
    }

    @Test
    void runCleanup_alreadyRunning_returnsBadRequest() {
        when(housekeepingService.runCleanup()).thenReturn(null);

        var result = housekeepingController.runCleanup();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Housekeeping job already running");
        assertThat(body).containsEntry("message", "Please wait for the current job to complete");
        verify(housekeepingService).runCleanup();
    }

    @Test
    void runCleanup_serviceThrows_propagatesException() {
        when(housekeepingService.runCleanup()).thenThrow(new RuntimeException("Cleanup failed"));

        assertThatThrownBy(() -> housekeepingController.runCleanup())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cleanup failed");
    }

    @Test
    void runCleanup_runningJob_returnsRunningStatus() {
        var job = runningJob();
        when(housekeepingService.runCleanup()).thenReturn(job);

        var result = housekeepingController.runCleanup();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("status", "running");
        assertThat(body).containsEntry("recordsProcessed", 50);
    }

    @Test
    void runCleanup_zeroRecords_returnsOkWithZeros() {
        var job = HousekeepingJob.builder()
                .id(UUID.randomUUID())
                .jobType("execution_cleanup")
                .startedAt(Instant.now())
                .status("completed")
                .recordsProcessed(0)
                .recordsArchived(0)
                .recordsDeleted(0)
                .build();
        when(housekeepingService.runCleanup()).thenReturn(job);

        var result = housekeepingController.runCleanup();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("recordsProcessed", 0);
        assertThat(body).containsEntry("recordsArchived", 0);
        assertThat(body).containsEntry("recordsDeleted", 0);
    }

    // ===== getJobHistory (GET /api/admin/housekeeping/jobs) =====

    @Test
    void getJobHistory_returnsPageOfJobs() {
        var job1 = sampleJob();
        var job2 = sampleJob();
        Page<HousekeepingJob> page = new PageImpl<>(List.of(job1, job2), PageRequest.of(0, 20), 2);
        when(jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 20))).thenReturn(page);

        var result = housekeepingController.getJobHistory(0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(2);
        assertThat(result.getBody().getContent()).hasSize(2);
        verify(jobRepository).findAllByOrderByStartedAtDesc(PageRequest.of(0, 20));
    }

    @Test
    void getJobHistory_emptyPage_returnsOkWithEmptyPage() {
        Page<HousekeepingJob> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 20))).thenReturn(emptyPage);

        var result = housekeepingController.getJobHistory(0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
        assertThat(result.getBody().getContent()).isEmpty();
    }

    @Test
    void getJobHistory_customPageAndSize_usesCorrectPageRequest() {
        Page<HousekeepingJob> page = new PageImpl<>(List.of(), PageRequest.of(2, 50), 0);
        when(jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(2, 50))).thenReturn(page);

        var result = housekeepingController.getJobHistory(2, 50);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jobRepository).findAllByOrderByStartedAtDesc(PageRequest.of(2, 50));
    }

    @Test
    void getJobHistory_singleJob_returnsPageWithOneElement() {
        var job = sampleJob();
        Page<HousekeepingJob> page = new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1);
        when(jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 20))).thenReturn(page);

        var result = housekeepingController.getJobHistory(0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getJobType()).isEqualTo("execution_cleanup");
    }

    @Test
    void getJobHistory_multiplePages_returnsCorrectPage() {
        var job = sampleJob();
        Page<HousekeepingJob> page = new PageImpl<>(List.of(job), PageRequest.of(1, 10), 25);
        when(jobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(1, 10))).thenReturn(page);

        var result = housekeepingController.getJobHistory(1, 10);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(25);
        assertThat(result.getBody().getTotalPages()).isEqualTo(3);
        assertThat(result.getBody().getContent()).hasSize(1);
    }

    // ===== getJob (GET /api/admin/housekeeping/jobs/{id}) =====

    @Test
    void getJob_found_returnsOk() {
        var jobId = UUID.randomUUID();
        var job = sampleJob();
        job.setId(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        var result = housekeepingController.getJob(jobId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(jobId);
        assertThat(result.getBody().getJobType()).isEqualTo("execution_cleanup");
        assertThat(result.getBody().getStatus()).isEqualTo("completed");
        assertThat(result.getBody().getRecordsProcessed()).isEqualTo(100);
        verify(jobRepository).findById(jobId);
    }

    @Test
    void getJob_notFound_returnsNotFound() {
        var jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        var result = housekeepingController.getJob(jobId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
        verify(jobRepository).findById(jobId);
    }

    @Test
    void getJob_completedJob_returnsAllFields() {
        var jobId = UUID.randomUUID();
        var job = HousekeepingJob.builder()
                .id(jobId)
                .jobType("execution_cleanup")
                .startedAt(Instant.parse("2026-01-15T02:00:00Z"))
                .completedAt(Instant.parse("2026-01-15T02:05:30Z"))
                .status("completed")
                .recordsProcessed(500)
                .recordsArchived(400)
                .recordsDeleted(100)
                .details(Map.of("retentionDays", 30))
                .build();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        var result = housekeepingController.getJob(jobId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStartedAt()).isEqualTo(Instant.parse("2026-01-15T02:00:00Z"));
        assertThat(result.getBody().getCompletedAt()).isEqualTo(Instant.parse("2026-01-15T02:05:30Z"));
        assertThat(result.getBody().getRecordsArchived()).isEqualTo(400);
        assertThat(result.getBody().getDetails()).containsEntry("retentionDays", 30);
    }

    @Test
    void getJob_failedJob_returnsErrorMessage() {
        var jobId = UUID.randomUUID();
        var job = HousekeepingJob.builder()
                .id(jobId)
                .jobType("execution_cleanup")
                .startedAt(Instant.now())
                .status("failed")
                .errorMessage("Database connection timeout")
                .recordsProcessed(0)
                .recordsArchived(0)
                .recordsDeleted(0)
                .build();
        job.fail("Database connection timeout");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        var result = housekeepingController.getJob(jobId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("failed");
        assertThat(result.getBody().getErrorMessage()).isEqualTo("Database connection timeout");
    }

    // ===== cleanupHistory (POST /api/admin/housekeeping/cleanup-history) =====

    @Test
    void cleanupHistory_success_returnsOkWithDeletedCount() {
        when(housekeepingService.cleanupOldHistory()).thenReturn(150);

        var result = housekeepingController.cleanupHistory();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("recordsDeleted", 150);
        assertThat(result.getBody()).containsEntry("message", "History cleanup completed");
        verify(housekeepingService).cleanupOldHistory();
    }

    @Test
    void cleanupHistory_noRecordsToDelete_returnsZero() {
        when(housekeepingService.cleanupOldHistory()).thenReturn(0);

        var result = housekeepingController.cleanupHistory();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("recordsDeleted", 0);
        assertThat(result.getBody()).containsEntry("message", "History cleanup completed");
    }

    @Test
    void cleanupHistory_serviceThrows_propagatesException() {
        when(housekeepingService.cleanupOldHistory()).thenThrow(new RuntimeException("Cleanup failed"));

        assertThatThrownBy(() -> housekeepingController.cleanupHistory())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cleanup failed");
    }

    @Test
    void cleanupHistory_largeDeleteCount_returnsCorrectCount() {
        when(housekeepingService.cleanupOldHistory()).thenReturn(50000);

        var result = housekeepingController.cleanupHistory();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("recordsDeleted", 50000);
    }

    // ===== Response body structure verification =====

    @Test
    void runCleanup_responseContainsExpectedKeys() {
        var job = sampleJob();
        when(housekeepingService.runCleanup()).thenReturn(job);

        var result = housekeepingController.runCleanup();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsOnlyKeys("jobId", "status", "recordsProcessed", "recordsArchived", "recordsDeleted");
    }

    @Test
    void runCleanup_badRequest_responseContainsExpectedKeys() {
        when(housekeepingService.runCleanup()).thenReturn(null);

        var result = housekeepingController.runCleanup();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsOnlyKeys("error", "message");
    }

    @Test
    void cleanupHistory_responseContainsExpectedKeys() {
        when(housekeepingService.cleanupOldHistory()).thenReturn(10);

        var result = housekeepingController.cleanupHistory();

        assertThat(result.getBody()).containsOnlyKeys("recordsDeleted", "message");
    }

    // ===== Interaction verification =====

    @Test
    void getStatistics_callsServiceExactlyOnce() {
        when(housekeepingService.getStatistics()).thenReturn(Map.of());

        housekeepingController.getStatistics();

        verify(housekeepingService, times(1)).getStatistics();
        verifyNoMoreInteractions(housekeepingService);
        verifyNoInteractions(jobRepository);
    }

    @Test
    void runCleanup_callsServiceExactlyOnce() {
        when(housekeepingService.runCleanup()).thenReturn(sampleJob());

        housekeepingController.runCleanup();

        verify(housekeepingService, times(1)).runCleanup();
        verifyNoMoreInteractions(housekeepingService);
        verifyNoInteractions(jobRepository);
    }

    @Test
    void getJobHistory_callsRepositoryOnly() {
        Page<HousekeepingJob> page = new PageImpl<>(List.of());
        when(jobRepository.findAllByOrderByStartedAtDesc(any())).thenReturn(page);

        housekeepingController.getJobHistory(0, 20);

        verify(jobRepository, times(1)).findAllByOrderByStartedAtDesc(PageRequest.of(0, 20));
        verifyNoMoreInteractions(jobRepository);
        verifyNoInteractions(housekeepingService);
    }

    @Test
    void getJob_callsRepositoryOnly() {
        var jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        housekeepingController.getJob(jobId);

        verify(jobRepository, times(1)).findById(jobId);
        verifyNoMoreInteractions(jobRepository);
        verifyNoInteractions(housekeepingService);
    }

    @Test
    void cleanupHistory_callsServiceExactlyOnce() {
        when(housekeepingService.cleanupOldHistory()).thenReturn(0);

        housekeepingController.cleanupHistory();

        verify(housekeepingService, times(1)).cleanupOldHistory();
        verifyNoMoreInteractions(housekeepingService);
        verifyNoInteractions(jobRepository);
    }
}
