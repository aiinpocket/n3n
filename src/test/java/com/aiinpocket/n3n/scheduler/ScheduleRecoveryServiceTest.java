package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScheduleRecoveryServiceTest extends BaseServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SchedulerService schedulerService;

    @InjectMocks
    private ScheduleRecoveryService scheduleRecoveryService;

    @Nested
    @DisplayName("Recover Schedules")
    class RecoverSchedules {

        @Test
        @DisplayName("No active schedules - logs info and does not attempt recovery")
        void recoverSchedules_noActiveSchedules_doesNotRecover() {
            when(scheduleRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());

            scheduleRecoveryService.recoverSchedules();

            verify(scheduleRepository).findByIsActiveTrue();
            verifyNoInteractions(schedulerService);
            verify(scheduleRepository, never()).save(any(Schedule.class));
        }

        @Test
        @DisplayName("2 active schedules - both recovered with quartzId updated")
        void recoverSchedules_twoActiveSchedules_bothRecovered() throws Exception {
            UUID flowId1 = UUID.randomUUID();
            UUID flowId2 = UUID.randomUUID();
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            Schedule schedule1 = Schedule.builder()
                    .id(UUID.randomUUID())
                    .flowId(flowId1)
                    .name("Schedule 1")
                    .cronExpression("0 0 9 * * ?")
                    .timezone("UTC")
                    .isActive(true)
                    .createdBy(userId1)
                    .build();

            Schedule schedule2 = Schedule.builder()
                    .id(UUID.randomUUID())
                    .flowId(flowId2)
                    .name("Schedule 2")
                    .cronExpression("0 30 12 * * ?")
                    .timezone("Asia/Taipei")
                    .isActive(true)
                    .createdBy(userId2)
                    .build();

            when(scheduleRepository.findByIsActiveTrue()).thenReturn(List.of(schedule1, schedule2));
            when(schedulerService.scheduleCron(eq(flowId1), eq("0 0 9 * * ?"), eq("UTC"), eq(userId1)))
                    .thenReturn("quartz-id-1");
            when(schedulerService.scheduleCron(eq(flowId2), eq("0 30 12 * * ?"), eq("Asia/Taipei"), eq(userId2)))
                    .thenReturn("quartz-id-2");
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

            scheduleRecoveryService.recoverSchedules();

            verify(schedulerService).scheduleCron(flowId1, "0 0 9 * * ?", "UTC", userId1);
            verify(schedulerService).scheduleCron(flowId2, "0 30 12 * * ?", "Asia/Taipei", userId2);
            verify(scheduleRepository, times(2)).save(any(Schedule.class));

            // Verify quartzScheduleId was set on each schedule
            verify(scheduleRepository).save(argThat(s -> "quartz-id-1".equals(s.getQuartzScheduleId())));
            verify(scheduleRepository).save(argThat(s -> "quartz-id-2".equals(s.getQuartzScheduleId())));
        }

        @Test
        @DisplayName("1 succeeds, 1 fails - partial recovery with correct counts")
        void recoverSchedules_partialFailure_recoversOneAndLogsFailure() throws Exception {
            UUID flowId1 = UUID.randomUUID();
            UUID flowId2 = UUID.randomUUID();
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            Schedule schedule1 = Schedule.builder()
                    .id(UUID.randomUUID())
                    .flowId(flowId1)
                    .name("Good Schedule")
                    .cronExpression("0 0 9 * * ?")
                    .timezone("UTC")
                    .isActive(true)
                    .createdBy(userId1)
                    .build();

            Schedule schedule2 = Schedule.builder()
                    .id(UUID.randomUUID())
                    .flowId(flowId2)
                    .name("Bad Schedule")
                    .cronExpression("0 0 12 * * ?")
                    .timezone("UTC")
                    .isActive(true)
                    .createdBy(userId2)
                    .build();

            when(scheduleRepository.findByIsActiveTrue()).thenReturn(List.of(schedule1, schedule2));
            when(schedulerService.scheduleCron(eq(flowId1), eq("0 0 9 * * ?"), eq("UTC"), eq(userId1)))
                    .thenReturn("quartz-id-success");
            when(schedulerService.scheduleCron(eq(flowId2), eq("0 0 12 * * ?"), eq("UTC"), eq(userId2)))
                    .thenThrow(new RuntimeException("Scheduler unavailable"));
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

            scheduleRecoveryService.recoverSchedules();

            // Only the successful one should be saved
            verify(scheduleRepository, times(1)).save(any(Schedule.class));
            verify(scheduleRepository).save(argThat(s -> "quartz-id-success".equals(s.getQuartzScheduleId())));

            // Both should have been attempted
            verify(schedulerService).scheduleCron(flowId1, "0 0 9 * * ?", "UTC", userId1);
            verify(schedulerService).scheduleCron(flowId2, "0 0 12 * * ?", "UTC", userId2);
        }

        @Test
        @DisplayName("Exception during recovery does not crash the service")
        void recoverSchedules_allFail_doesNotThrow() throws Exception {
            UUID flowId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Schedule schedule = Schedule.builder()
                    .id(UUID.randomUUID())
                    .flowId(flowId)
                    .name("Failing Schedule")
                    .cronExpression("0 0 6 * * ?")
                    .timezone("UTC")
                    .isActive(true)
                    .createdBy(userId)
                    .build();

            when(scheduleRepository.findByIsActiveTrue()).thenReturn(List.of(schedule));
            when(schedulerService.scheduleCron(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Quartz exploded"));

            // Should NOT throw - exception is caught internally
            scheduleRecoveryService.recoverSchedules();

            verify(schedulerService).scheduleCron(flowId, "0 0 6 * * ?", "UTC", userId);
            verify(scheduleRepository, never()).save(any(Schedule.class));
        }
    }
}
