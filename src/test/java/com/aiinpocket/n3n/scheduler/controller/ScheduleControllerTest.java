package com.aiinpocket.n3n.scheduler.controller;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowShareService flowShareService;

    @InjectMocks
    private ScheduleController scheduleController;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private Schedule sampleSchedule() {
        return Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .name("Test Schedule")
                .cronExpression("0 0 * * * ?")
                .timezone("UTC")
                .isActive(true)
                .createdBy(userId)
                .quartzScheduleId("quartz-" + UUID.randomUUID())
                .nextRunAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Flow sampleFlow(UUID flowId) {
        return Flow.builder()
                .id(flowId)
                .name("Test Flow")
                .createdBy(userId)
                .build();
    }

    // ========== listSchedules ==========

    @Test
    void listSchedules_shouldReturnAllUserSchedules() {
        Schedule s1 = sampleSchedule();
        Schedule s2 = sampleSchedule();
        List<Schedule> schedules = List.of(s1, s2);

        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId)).thenReturn(schedules);

        Set<UUID> flowIds = Set.of(s1.getFlowId(), s2.getFlowId());
        Flow f1 = sampleFlow(s1.getFlowId());
        Flow f2 = sampleFlow(s2.getFlowId());
        when(flowRepository.findByIdInAndIsDeletedFalse(flowIds)).thenReturn(List.of(f1, f2));

        var response = scheduleController.listSchedules(testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Test Schedule");
    }

    @Test
    void listSchedules_shouldReturnEmptyListWhenNoSchedules() {
        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(flowRepository.findByIdInAndIsDeletedFalse(Collections.emptySet())).thenReturn(List.of());

        var response = scheduleController.listSchedules(testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listSchedules_shouldHandleDeletedFlowsGracefully() {
        Schedule s1 = sampleSchedule();
        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId)).thenReturn(List.of(s1));
        // Flow was deleted, so findByIdInAndIsDeletedFalse returns empty
        when(flowRepository.findByIdInAndIsDeletedFalse(Set.of(s1.getFlowId()))).thenReturn(List.of());

        var response = scheduleController.listSchedules(testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getFlowName()).isNull();
    }

    // ========== getSchedule ==========

    @Test
    void getSchedule_shouldReturnScheduleWhenOwner() {
        Schedule schedule = sampleSchedule();
        Flow flow = sampleFlow(schedule.getFlowId());

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.of(flow));

        var response = scheduleController.getSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(schedule.getId());
        assertThat(response.getBody().getFlowName()).isEqualTo("Test Flow");
    }

    @Test
    void getSchedule_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.getSchedule(scheduleId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSchedule_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID()); // different user

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleController.getSchedule(schedule.getId(), testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSchedule_shouldReturnNullFlowNameWhenFlowDeleted() {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.getSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFlowName()).isNull();
    }

    // ========== createSchedule ==========

    @Test
    void createSchedule_shouldCreateSuccessfully() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("New Schedule");
        request.setCronExpression("0 0 * * * ?");
        request.setTimezone("Asia/Taipei");
        request.setInput(Map.of("key", "value"));

        String quartzId = "quartz-" + UUID.randomUUID();
        Date nextFire = Date.from(Instant.now().plusSeconds(3600));

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(0L);
        when(schedulerService.scheduleCron(eq(flowId), eq("0 0 * * * ?"), eq("Asia/Taipei"), eq(userId)))
                .thenReturn(quartzId);
        when(schedulerService.getNextFireTime(quartzId)).thenReturn(nextFire);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));

        var response = scheduleController.createSchedule(request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("New Schedule");
        assertThat(response.getBody().getCronExpression()).isEqualTo("0 0 * * * ?");
        assertThat(response.getBody().getTimezone()).isEqualTo("Asia/Taipei");
        assertThat(response.getBody().getFlowName()).isEqualTo("Test Flow");

        verify(schedulerService).scheduleCron(flowId, "0 0 * * * ?", "Asia/Taipei", userId);
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    void createSchedule_shouldUseUtcWhenTimezoneIsNull() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("UTC Schedule");
        request.setCronExpression("0 0 12 * * ?");
        request.setTimezone(null);

        String quartzId = "quartz-utc";

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(0L);
        when(schedulerService.scheduleCron(eq(flowId), anyString(), eq("UTC"), eq(userId)))
                .thenReturn(quartzId);
        when(schedulerService.getNextFireTime(quartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var response = scheduleController.createSchedule(request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getTimezone()).isEqualTo("UTC");

        verify(schedulerService).scheduleCron(flowId, "0 0 12 * * ?", "UTC", userId);
    }

    @Test
    void createSchedule_shouldThrowNotFoundWhenFlowDoesNotExist() {
        UUID flowId = UUID.randomUUID();

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Schedule");
        request.setCronExpression("0 0 * * * ?");

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.createSchedule(request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSchedule_shouldThrowAccessDeniedWhenNoEditAccess() {
        UUID flowId = UUID.randomUUID();
        Flow flow = Flow.builder()
                .id(flowId)
                .name("Other Flow")
                .createdBy(UUID.randomUUID()) // different user
                .build();

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Schedule");
        request.setCronExpression("0 0 * * * ?");

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> scheduleController.createSchedule(request, testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createSchedule_shouldAllowSharedFlowWithEditPermission() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Flow flow = Flow.builder()
                .id(flowId)
                .name("Shared Flow")
                .createdBy(otherUserId) // owned by different user
                .build();

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Shared Schedule");
        request.setCronExpression("0 0 * * * ?");

        String quartzId = "quartz-shared";

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true); // shared with edit
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(0L);
        when(schedulerService.scheduleCron(eq(flowId), anyString(), anyString(), eq(userId)))
                .thenReturn(quartzId);
        when(schedulerService.getNextFireTime(quartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var response = scheduleController.createSchedule(request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("Shared Schedule");
    }

    @Test
    void createSchedule_shouldThrowWhenScheduleLimitExceeded() {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Schedule");
        request.setCronExpression("0 0 * * * ?");

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(100L);

        assertThatThrownBy(() -> scheduleController.createSchedule(request, testUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Schedule limit exceeded");
    }

    @Test
    void createSchedule_shouldThrowWhenSchedulerFails() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Failing Schedule");
        request.setCronExpression("0 0 * * * ?");

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(0L);
        when(schedulerService.scheduleCron(eq(flowId), anyString(), anyString(), eq(userId)))
                .thenThrow(new SchedulerException("Quartz error"));

        assertThatThrownBy(() -> scheduleController.createSchedule(request, testUser()))
                .isInstanceOf(SchedulerException.class);
    }

    // ========== updateSchedule ==========

    @Test
    void updateSchedule_shouldUpdateNameOnly() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setName("Updated Name");

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated Name");

        // No Quartz re-scheduling for name-only change
        verify(schedulerService, never()).scheduleCron(any(), any(), any(), any());
        verify(schedulerService, never()).unschedule(any());
    }

    @Test
    void updateSchedule_shouldRescheduleCronWhenCronChanged() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        String oldQuartzId = schedule.getQuartzScheduleId();
        String newQuartzId = "quartz-new-" + UUID.randomUUID();
        Date newNextFire = Date.from(Instant.now().plusSeconds(7200));

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setCronExpression("0 30 * * * ?");

        when(schedulerService.scheduleCron(eq(schedule.getFlowId()), eq("0 30 * * * ?"),
                eq(schedule.getTimezone()), eq(userId))).thenReturn(newQuartzId);
        when(schedulerService.getNextFireTime(newQuartzId)).thenReturn(newNextFire);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCronExpression()).isEqualTo("0 30 * * * ?");

        verify(schedulerService).scheduleCron(schedule.getFlowId(), "0 30 * * * ?", schedule.getTimezone(), userId);
        verify(schedulerService).unschedule(oldQuartzId);
    }

    @Test
    void updateSchedule_shouldRescheduleWhenTimezoneChanged() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        String oldQuartzId = schedule.getQuartzScheduleId();
        String newQuartzId = "quartz-tz-" + UUID.randomUUID();

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setTimezone("Asia/Tokyo");

        when(schedulerService.scheduleCron(eq(schedule.getFlowId()), eq(schedule.getCronExpression()),
                eq("Asia/Tokyo"), eq(userId))).thenReturn(newQuartzId);
        when(schedulerService.getNextFireTime(newQuartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTimezone()).isEqualTo("Asia/Tokyo");

        verify(schedulerService).scheduleCron(schedule.getFlowId(), schedule.getCronExpression(), "Asia/Tokyo", userId);
        verify(schedulerService).unschedule(oldQuartzId);
    }

    @Test
    void updateSchedule_shouldUpdateInputWithoutReschedule() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setInput(Map.of("newKey", "newVal"));

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getInput()).containsEntry("newKey", "newVal");

        verify(schedulerService, never()).scheduleCron(any(), any(), any(), any());
    }

    @Test
    void updateSchedule_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setName("New Name");

        assertThatThrownBy(() -> scheduleController.updateSchedule(scheduleId, request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSchedule_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID()); // different user

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setName("New Name");

        assertThatThrownBy(() -> scheduleController.updateSchedule(schedule.getId(), request, testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateSchedule_shouldThrowWhenBlankCronExpression() {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setCronExpression("   ");

        assertThatThrownBy(() -> scheduleController.updateSchedule(schedule.getId(), request, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cron expression must not be blank");
    }

    // ========== deleteSchedule ==========

    @Test
    void deleteSchedule_shouldDeleteSuccessfully() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.deleteSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(schedulerService).unschedule(schedule.getQuartzScheduleId());
        verify(scheduleRepository).deleteById(schedule.getId());
    }

    @Test
    void deleteSchedule_shouldSkipUnscheduleWhenNoQuartzId() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setQuartzScheduleId(null);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.deleteSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(schedulerService, never()).unschedule(any());
        verify(scheduleRepository).deleteById(schedule.getId());
    }

    @Test
    void deleteSchedule_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.deleteSchedule(scheduleId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSchedule_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID());

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleController.deleteSchedule(schedule.getId(), testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ========== pauseSchedule ==========

    @Test
    void pauseSchedule_shouldPauseSuccessfully() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.pauseSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();

        verify(schedulerService).pause(schedule.getQuartzScheduleId());
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    void pauseSchedule_shouldSkipQuartzPauseWhenNoQuartzId() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setQuartzScheduleId(null);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.pauseSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();

        verify(schedulerService, never()).pause(any());
    }

    @Test
    void pauseSchedule_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.pauseSchedule(scheduleId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pauseSchedule_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID());

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleController.pauseSchedule(schedule.getId(), testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ========== resumeSchedule ==========

    @Test
    void resumeSchedule_shouldResumeSuccessfully() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setIsActive(false);
        Date nextFire = Date.from(Instant.now().plusSeconds(3600));

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(schedulerService.getNextFireTime(schedule.getQuartzScheduleId())).thenReturn(nextFire);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.resumeSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isTrue();
        assertThat(response.getBody().getNextRunAt()).isEqualTo(nextFire.toInstant());

        verify(schedulerService).resume(schedule.getQuartzScheduleId());
    }

    @Test
    void resumeSchedule_shouldSkipQuartzResumeWhenNoQuartzId() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setIsActive(false);
        schedule.setQuartzScheduleId(null);

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.resumeSchedule(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isTrue();

        verify(schedulerService, never()).resume(any());
    }

    @Test
    void resumeSchedule_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.resumeSchedule(scheduleId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resumeSchedule_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID());

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleController.resumeSchedule(schedule.getId(), testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ========== triggerScheduleNow ==========

    @Test
    void triggerScheduleNow_shouldTriggerActiveSchedule() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.triggerScheduleNow(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("message", "Schedule triggered successfully");

        verify(schedulerService).triggerNow(schedule.getQuartzScheduleId());
    }

    @Test
    void triggerScheduleNow_shouldReturnBadRequestWhenPaused() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setIsActive(false);

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.triggerScheduleNow(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("error", "Cannot trigger paused schedule");

        verify(schedulerService, never()).triggerNow(any());
    }

    @Test
    void triggerScheduleNow_shouldReturnBadRequestWhenNoQuartzJob() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setQuartzScheduleId(null);

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.triggerScheduleNow(schedule.getId(), testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("error", "Schedule has no active Quartz job");

        verify(schedulerService, never()).triggerNow(any());
    }

    @Test
    void triggerScheduleNow_shouldThrowNotFoundWhenScheduleDoesNotExist() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleController.triggerScheduleNow(scheduleId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void triggerScheduleNow_shouldThrowAccessDeniedWhenNotOwner() {
        Schedule schedule = sampleSchedule();
        schedule.setCreatedBy(UUID.randomUUID());

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleController.triggerScheduleNow(schedule.getId(), testUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void triggerScheduleNow_shouldReturnBadRequestWhenIsActiveIsNull() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setIsActive(null);

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        var response = scheduleController.triggerScheduleNow(schedule.getId(), testUser());

        // Boolean.TRUE.equals(null) => false, so treated as paused
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    // ========== Edge cases ==========

    @Test
    void createSchedule_shouldHandleNullNextFireTime() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("No Next Fire");
        request.setCronExpression("0 0 * * * ?");

        String quartzId = "quartz-no-fire";

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(0L);
        when(schedulerService.scheduleCron(eq(flowId), anyString(), anyString(), eq(userId)))
                .thenReturn(quartzId);
        when(schedulerService.getNextFireTime(quartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var response = scheduleController.createSchedule(request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getNextRunAt()).isNull();
    }

    @Test
    void createSchedule_shouldAllowUpTo99Schedules() throws SchedulerException {
        UUID flowId = UUID.randomUUID();
        Flow flow = sampleFlow(flowId);

        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setFlowId(flowId);
        request.setName("Schedule 100");
        request.setCronExpression("0 0 * * * ?");

        String quartzId = "quartz-99";

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(scheduleRepository.countByCreatedBy(userId)).thenReturn(99L); // 99 existing, will be 100th
        when(schedulerService.scheduleCron(eq(flowId), anyString(), anyString(), eq(userId)))
                .thenReturn(quartzId);
        when(schedulerService.getNextFireTime(quartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var response = scheduleController.createSchedule(request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void updateSchedule_shouldNotRescheduleWhenQuartzIdIsNull() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setQuartzScheduleId(null);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setCronExpression("0 15 * * * ?");

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // cronChanged is true, but quartzScheduleId is null, so no reschedule
        verify(schedulerService, never()).scheduleCron(any(), any(), any(), any());
        verify(schedulerService, never()).unschedule(any());
    }

    @Test
    void resumeSchedule_shouldHandleSchedulerExceptionInGetNextFireTime() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        schedule.setIsActive(false);

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(schedulerService.getNextFireTime(schedule.getQuartzScheduleId()))
                .thenThrow(new SchedulerException("Quartz error"));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.resumeSchedule(schedule.getId(), testUser());

        // getNextRunInstant catches SchedulerException and returns null
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isTrue();
        assertThat(response.getBody().getNextRunAt()).isNull();
    }

    @Test
    void updateSchedule_shouldUpdateBothCronAndTimezoneInOneRequest() throws SchedulerException {
        Schedule schedule = sampleSchedule();
        String oldQuartzId = schedule.getQuartzScheduleId();
        String newQuartzId = "quartz-both-" + UUID.randomUUID();

        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setCronExpression("0 45 * * * ?");
        request.setTimezone("Europe/London");

        when(schedulerService.scheduleCron(eq(schedule.getFlowId()), eq("0 45 * * * ?"),
                eq("Europe/London"), eq(userId))).thenReturn(newQuartzId);
        when(schedulerService.getNextFireTime(newQuartzId)).thenReturn(null);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowRepository.findByIdAndIsDeletedFalse(schedule.getFlowId())).thenReturn(Optional.empty());

        var response = scheduleController.updateSchedule(schedule.getId(), request, testUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCronExpression()).isEqualTo("0 45 * * * ?");
        assertThat(response.getBody().getTimezone()).isEqualTo("Europe/London");

        // Should only call scheduleCron once (not twice for cron + timezone separately)
        verify(schedulerService, times(1)).scheduleCron(any(), any(), any(), any());
        verify(schedulerService).unschedule(oldQuartzId);
    }
}
