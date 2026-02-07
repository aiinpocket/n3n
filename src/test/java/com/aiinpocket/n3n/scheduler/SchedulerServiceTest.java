package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.quartz.*;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchedulerServiceTest extends BaseServiceTest {

    @Mock
    private Scheduler scheduler;

    @InjectMocks
    private SchedulerService schedulerService;

    private UUID flowId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        flowId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Schedule Cron")
    class ScheduleCron {

        @Test
        void scheduleCron_validParams_returnsScheduleId() throws SchedulerException {
            String scheduleId = schedulerService.scheduleCron(
                flowId, "0 0 9 * * ?", "Asia/Taipei", userId
            );

            assertThat(scheduleId).isNotNull().isNotBlank();
            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        void scheduleCron_jobContainsFlowIdAndUserId() throws SchedulerException {
            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);

            schedulerService.scheduleCron(flowId, "0 0 9 * * ?", "UTC", userId);

            verify(scheduler).scheduleJob(jobCaptor.capture(), any(Trigger.class));
            JobDetail job = jobCaptor.getValue();
            assertThat(job.getJobDataMap().getString("flowId")).isEqualTo(flowId.toString());
            assertThat(job.getJobDataMap().getString("userId")).isEqualTo(userId.toString());
        }

        @Test
        void scheduleCron_jobIsWorkflowExecutionJob() throws SchedulerException {
            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);

            schedulerService.scheduleCron(flowId, "0 0 9 * * ?", "UTC", userId);

            verify(scheduler).scheduleJob(jobCaptor.capture(), any(Trigger.class));
            assertThat(jobCaptor.getValue().getJobClass()).isEqualTo(WorkflowExecutionJob.class);
        }
    }

    @Nested
    @DisplayName("Schedule Interval")
    class ScheduleInterval {

        @Test
        void scheduleInterval_validParams_returnsScheduleId() throws SchedulerException {
            String scheduleId = schedulerService.scheduleInterval(flowId, 60000, userId);

            assertThat(scheduleId).isNotNull().isNotBlank();
            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        void scheduleInterval_jobContainsCorrectData() throws SchedulerException {
            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);

            schedulerService.scheduleInterval(flowId, 30000, userId);

            verify(scheduler).scheduleJob(jobCaptor.capture(), any(Trigger.class));
            JobDetail job = jobCaptor.getValue();
            assertThat(job.getJobDataMap().getString("flowId")).isEqualTo(flowId.toString());
            assertThat(job.getJobDataMap().getString("userId")).isEqualTo(userId.toString());
        }
    }

    @Nested
    @DisplayName("Unschedule")
    class Unschedule {

        @Test
        void unschedule_existing_returnsTrue() throws SchedulerException {
            String scheduleId = "test-schedule-id";
            when(scheduler.unscheduleJob(any(TriggerKey.class))).thenReturn(true);

            boolean result = schedulerService.unschedule(scheduleId);

            assertThat(result).isTrue();
            verify(scheduler).unscheduleJob(any(TriggerKey.class));
            verify(scheduler).deleteJob(any(JobKey.class));
        }

        @Test
        void unschedule_nonExisting_returnsFalse() throws SchedulerException {
            String scheduleId = "non-existing";
            when(scheduler.unscheduleJob(any(TriggerKey.class))).thenReturn(false);

            boolean result = schedulerService.unschedule(scheduleId);

            assertThat(result).isFalse();
            verify(scheduler, never()).deleteJob(any(JobKey.class));
        }
    }

    @Nested
    @DisplayName("Pause and Resume")
    class PauseResume {

        @Test
        void pause_delegatesToScheduler() throws SchedulerException {
            schedulerService.pause("test-id");

            verify(scheduler).pauseTrigger(TriggerKey.triggerKey("test-id", "workflow-triggers"));
        }

        @Test
        void resume_delegatesToScheduler() throws SchedulerException {
            schedulerService.resume("test-id");

            verify(scheduler).resumeTrigger(TriggerKey.triggerKey("test-id", "workflow-triggers"));
        }
    }

    @Nested
    @DisplayName("Exists")
    class Exists {

        @Test
        void exists_existingSchedule_returnsTrue() throws SchedulerException {
            when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);

            boolean result = schedulerService.exists("test-id");

            assertThat(result).isTrue();
        }

        @Test
        void exists_nonExisting_returnsFalse() throws SchedulerException {
            when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);

            boolean result = schedulerService.exists("test-id");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Get Next Fire Time")
    class GetNextFireTime {

        @Test
        void getNextFireTime_existingTrigger_returnsDate() throws SchedulerException {
            Date expectedDate = new Date();
            Trigger trigger = mock(Trigger.class);
            when(trigger.getNextFireTime()).thenReturn(expectedDate);
            when(scheduler.getTrigger(any(TriggerKey.class))).thenReturn(trigger);

            Date result = schedulerService.getNextFireTime("test-id");

            assertThat(result).isEqualTo(expectedDate);
        }

        @Test
        void getNextFireTime_noTrigger_returnsNull() throws SchedulerException {
            when(scheduler.getTrigger(any(TriggerKey.class))).thenReturn(null);

            Date result = schedulerService.getNextFireTime("test-id");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Trigger Now")
    class TriggerNow {

        @Test
        void triggerNow_delegatesToScheduler() throws SchedulerException {
            schedulerService.triggerNow("test-id");

            verify(scheduler).triggerJob(JobKey.jobKey("test-id", "workflow-schedules"));
        }
    }

    @Nested
    @DisplayName("Schedule Cron - Edge Cases")
    class ScheduleCronEdgeCases {

        @Test
        void scheduleCron_differentTimezone_usesProvidedTimezone() throws SchedulerException {
            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

            schedulerService.scheduleCron(flowId, "0 0 12 * * ?", "America/New_York", userId);

            verify(scheduler).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
            Trigger trigger = triggerCaptor.getValue();
            assertThat(trigger).isInstanceOf(CronTrigger.class);
        }

        @Test
        void scheduleCron_triggerGroupIsCorrect() throws SchedulerException {
            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

            schedulerService.scheduleCron(flowId, "0 0 9 * * ?", "UTC", userId);

            verify(scheduler).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
            Trigger trigger = triggerCaptor.getValue();
            assertThat(trigger.getKey().getGroup()).isEqualTo("workflow-triggers");
        }

        @Test
        void scheduleCron_schedulerException_propagates() throws SchedulerException {
            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                    .thenThrow(new SchedulerException("Scheduler is shutdown"));

            assertThatThrownBy(() -> schedulerService.scheduleCron(flowId, "0 0 9 * * ?", "UTC", userId))
                    .isInstanceOf(SchedulerException.class)
                    .hasMessageContaining("shutdown");
        }
    }

    @Nested
    @DisplayName("Schedule Interval - Edge Cases")
    class ScheduleIntervalEdgeCases {

        @Test
        void scheduleInterval_smallInterval_succeeds() throws SchedulerException {
            String scheduleId = schedulerService.scheduleInterval(flowId, 1000, userId);

            assertThat(scheduleId).isNotNull();
            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        void scheduleInterval_largeInterval_succeeds() throws SchedulerException {
            String scheduleId = schedulerService.scheduleInterval(flowId, 86400000, userId);

            assertThat(scheduleId).isNotNull();
            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }
    }

    @Nested
    @DisplayName("Unschedule - Edge Cases")
    class UnscheduleEdgeCases {

        @Test
        void unschedule_schedulerException_propagates() throws SchedulerException {
            when(scheduler.unscheduleJob(any(TriggerKey.class)))
                    .thenThrow(new SchedulerException("DB error"));

            assertThatThrownBy(() -> schedulerService.unschedule("some-id"))
                    .isInstanceOf(SchedulerException.class);
        }
    }

    @Nested
    @DisplayName("Pause and Resume - Edge Cases")
    class PauseResumeEdgeCases {

        @Test
        void pause_schedulerException_propagates() throws SchedulerException {
            doThrow(new SchedulerException("Not found")).when(scheduler)
                    .pauseTrigger(any(TriggerKey.class));

            assertThatThrownBy(() -> schedulerService.pause("test-id"))
                    .isInstanceOf(SchedulerException.class);
        }

        @Test
        void resume_schedulerException_propagates() throws SchedulerException {
            doThrow(new SchedulerException("Not found")).when(scheduler)
                    .resumeTrigger(any(TriggerKey.class));

            assertThatThrownBy(() -> schedulerService.resume("test-id"))
                    .isInstanceOf(SchedulerException.class);
        }
    }
}
