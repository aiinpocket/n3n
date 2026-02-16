package com.aiinpocket.n3n.scheduler.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import com.aiinpocket.n3n.scheduler.SchedulerService;
import com.aiinpocket.n3n.scheduler.dto.CreateScheduleRequest;
import com.aiinpocket.n3n.scheduler.dto.ScheduleResponse;
import com.aiinpocket.n3n.scheduler.dto.UpdateScheduleRequest;
import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedules", description = "Schedule management")
@Slf4j
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final SchedulerService schedulerService;
    private final FlowRepository flowRepository;
    private final FlowShareService flowShareService;
    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> listSchedules(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<Schedule> schedules = scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId);

        // Batch-load flow names to avoid N+1
        Set<UUID> flowIds = schedules.stream()
                .map(Schedule::getFlowId)
                .collect(Collectors.toSet());
        Map<UUID, String> flowNameMap = flowRepository.findByIdInAndIsDeletedFalse(flowIds)
                .stream()
                .collect(Collectors.toMap(Flow::getId, Flow::getName));

        List<ScheduleResponse> responses = schedules.stream()
                .map(s -> ScheduleResponse.from(s, flowNameMap.get(s.getFlowId())))
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);
        String flowName = flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())
                .map(Flow::getName)
                .orElse(null);
        return ResponseEntity.ok(ScheduleResponse.from(schedule, flowName));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ScheduleResponse> createSchedule(
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify the flow exists and user has edit access (owner or shared with edit permission)
        flowRepository.findByIdAndIsDeletedFalse(request.getFlowId())
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found"));
        if (!flowShareService.hasEditAccess(request.getFlowId(), userId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Limit schedules per user
        long userScheduleCount = scheduleRepository.countByCreatedBy(userId);
        if (userScheduleCount >= 100) {
            throw new IllegalStateException("Schedule limit exceeded. Maximum 100 schedules per user.");
        }

        String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";

        // Register the cron job in Quartz
        String quartzScheduleId = schedulerService.scheduleCron(
                request.getFlowId(), request.getCronExpression(), timezone, userId);

        // Compute next run time from Quartz
        Instant nextRunAt = getNextRunInstant(quartzScheduleId);

        // Persist the schedule entity
        Schedule schedule = Schedule.builder()
                .flowId(request.getFlowId())
                .name(request.getName())
                .cronExpression(request.getCronExpression())
                .timezone(timezone)
                .input(request.getInput())
                .nextRunAt(nextRunAt)
                .createdBy(userId)
                .quartzScheduleId(quartzScheduleId)
                .build();

        schedule = scheduleRepository.save(schedule);
        log.info("Schedule created: id={}, flowId={}, cron='{}'",
                schedule.getId(), schedule.getFlowId(), schedule.getCronExpression());

        activityService.logActivity(userId, "SCHEDULE_CREATE", "schedule", schedule.getId(),
                schedule.getName(), Map.of("flowId", schedule.getFlowId().toString(), "cron", schedule.getCronExpression()));

        String flowName = flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())
                .map(Flow::getName)
                .orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleResponse.from(schedule, flowName));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);

        boolean cronChanged = false;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new IllegalArgumentException("Name must not be blank");
            }
            schedule.setName(request.getName());
        }
        if (request.getCronExpression() != null) {
            if (request.getCronExpression().isBlank()) {
                throw new IllegalArgumentException("Cron expression must not be blank");
            }
            schedule.setCronExpression(request.getCronExpression());
            cronChanged = true;
        }
        if (request.getTimezone() != null) {
            if (request.getTimezone().isBlank()) {
                throw new IllegalArgumentException("Timezone must not be blank");
            }
            schedule.setTimezone(request.getTimezone());
            cronChanged = true;
        }
        if (request.getInput() != null) {
            schedule.setInput(request.getInput());
        }

        // Re-register in Quartz if cron or timezone changed
        // Schedule new job first to validate cron, then unschedule old one
        if (cronChanged && schedule.getQuartzScheduleId() != null) {
            String oldQuartzId = schedule.getQuartzScheduleId();
            String newQuartzId = schedulerService.scheduleCron(
                    schedule.getFlowId(), schedule.getCronExpression(),
                    schedule.getTimezone(), userId);
            schedulerService.unschedule(oldQuartzId);
            schedule.setQuartzScheduleId(newQuartzId);
            schedule.setNextRunAt(getNextRunInstant(newQuartzId));
        }

        schedule = scheduleRepository.save(schedule);
        log.info("Schedule updated: id={}, cron='{}'", id, schedule.getCronExpression());

        activityService.logActivity(userId, "SCHEDULE_UPDATE", "schedule", id,
                schedule.getName(), Map.of("cron", schedule.getCronExpression()));

        String flowName = flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())
                .map(Flow::getName)
                .orElse(null);
        return ResponseEntity.ok(ScheduleResponse.from(schedule, flowName));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);

        // Unschedule from Quartz
        if (schedule.getQuartzScheduleId() != null) {
            schedulerService.unschedule(schedule.getQuartzScheduleId());
        }

        activityService.logActivity(userId, "SCHEDULE_DELETE", "schedule", id,
                schedule.getName(), null);

        scheduleRepository.deleteById(id);
        log.info("Schedule deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    @Transactional
    public ResponseEntity<ScheduleResponse> pauseSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);

        if (schedule.getQuartzScheduleId() != null) {
            schedulerService.pause(schedule.getQuartzScheduleId());
        }
        schedule.setIsActive(false);
        schedule = scheduleRepository.save(schedule);

        log.info("Schedule paused: id={}", id);
        activityService.logActivity(userId, "SCHEDULE_PAUSE", "schedule", id, schedule.getName(), null);

        String flowName = flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())
                .map(Flow::getName)
                .orElse(null);
        return ResponseEntity.ok(ScheduleResponse.from(schedule, flowName));
    }

    @PostMapping("/{id}/resume")
    @Transactional
    public ResponseEntity<ScheduleResponse> resumeSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);

        if (schedule.getQuartzScheduleId() != null) {
            schedulerService.resume(schedule.getQuartzScheduleId());
        }
        schedule.setIsActive(true);
        schedule.setNextRunAt(getNextRunInstant(schedule.getQuartzScheduleId()));
        schedule = scheduleRepository.save(schedule);

        log.info("Schedule resumed: id={}", id);
        activityService.logActivity(userId, "SCHEDULE_RESUME", "schedule", id, schedule.getName(), null);

        String flowName = flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())
                .map(Flow::getName)
                .orElse(null);
        return ResponseEntity.ok(ScheduleResponse.from(schedule, flowName));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<Map<String, Object>> triggerScheduleNow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) throws SchedulerException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Schedule schedule = findScheduleWithOwnerCheck(id, userId);

        if (!Boolean.TRUE.equals(schedule.getIsActive())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Cannot trigger paused schedule"));
        }

        if (schedule.getQuartzScheduleId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Schedule has no active Quartz job"));
        }

        schedulerService.triggerNow(schedule.getQuartzScheduleId());
        log.info("Schedule triggered manually: id={}", id);
        activityService.logActivity(userId, "SCHEDULE_TRIGGER", "schedule", id, schedule.getName(), null);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Schedule triggered successfully"));
    }

    // ---- private helpers ----

    private Schedule findScheduleWithOwnerCheck(UUID id, UUID userId) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        if (!schedule.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        return schedule;
    }

    private Instant getNextRunInstant(String quartzScheduleId) {
        if (quartzScheduleId == null) {
            return null;
        }
        try {
            Date nextFire = schedulerService.getNextFireTime(quartzScheduleId);
            return nextFire != null ? nextFire.toInstant() : null;
        } catch (SchedulerException e) {
            log.warn("Failed to get next fire time for schedule {}: {}", quartzScheduleId, e.getMessage());
            return null;
        }
    }
}
