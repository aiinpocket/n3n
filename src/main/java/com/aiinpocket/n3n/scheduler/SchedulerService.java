package com.aiinpocket.n3n.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Service for managing scheduled workflow executions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulerService {

    private final Scheduler scheduler;

    private static final String JOB_GROUP = "workflow-schedules";
    private static final String TRIGGER_GROUP = "workflow-triggers";
    private static final long MIN_INTERVAL_MS = 10_000; // 10 seconds minimum

    /**
     * Schedule a workflow to run on a cron schedule.
     *
     * @param flowId the flow ID to execute
     * @param cronExpression the cron expression
     * @param timezone the timezone for the schedule
     * @param userId the user who owns this schedule
     * @return the schedule ID
     */
    public String scheduleCron(UUID flowId, String cronExpression, String timezone, UUID userId) throws SchedulerException {
        // Validate cron expression
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
        }

        // Validate timezone
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("Timezone is required");
        }
        try {
            ZoneId.of(timezone);
        } catch (ZoneRulesException e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }

        String scheduleId = UUID.randomUUID().toString();

        JobDetail job = JobBuilder.newJob(WorkflowExecutionJob.class)
            .withIdentity(scheduleId, JOB_GROUP)
            .usingJobData("flowId", flowId.toString())
            .usingJobData("userId", userId.toString())
            .usingJobData("scheduleId", scheduleId)
            .storeDurably()
            .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(scheduleId, TRIGGER_GROUP)
            .withSchedule(CronScheduleBuilder
                .cronSchedule(cronExpression)
                .inTimeZone(TimeZone.getTimeZone(ZoneId.of(timezone)))
                .withMisfireHandlingInstructionFireAndProceed())
            .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled flow {} with cron '{}' in timezone {}, scheduleId={}",
            flowId, cronExpression, timezone, scheduleId);

        return scheduleId;
    }

    /**
     * Schedule a workflow to run at fixed intervals.
     *
     * @param flowId the flow ID to execute
     * @param intervalMs the interval in milliseconds
     * @param userId the user who owns this schedule
     * @return the schedule ID
     */
    public String scheduleInterval(UUID flowId, long intervalMs, UUID userId) throws SchedulerException {
        if (intervalMs < MIN_INTERVAL_MS) {
            throw new IllegalArgumentException(
                "Interval must be at least " + MIN_INTERVAL_MS + "ms (got " + intervalMs + "ms)");
        }

        String scheduleId = UUID.randomUUID().toString();

        JobDetail job = JobBuilder.newJob(WorkflowExecutionJob.class)
            .withIdentity(scheduleId, JOB_GROUP)
            .usingJobData("flowId", flowId.toString())
            .usingJobData("userId", userId.toString())
            .usingJobData("scheduleId", scheduleId)
            .storeDurably()
            .build();

        SimpleTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(scheduleId, TRIGGER_GROUP)
            .withSchedule(SimpleScheduleBuilder
                .simpleSchedule()
                .withIntervalInMilliseconds(intervalMs)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithExistingCount())
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled flow {} with interval {}ms, scheduleId={}",
            flowId, intervalMs, scheduleId);

        return scheduleId;
    }

    /**
     * Unschedule a workflow.
     *
     * @param scheduleId the schedule ID
     * @return true if successfully unscheduled
     */
    public boolean unschedule(String scheduleId) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(scheduleId, TRIGGER_GROUP);
        boolean result = scheduler.unscheduleJob(triggerKey);

        if (result) {
            scheduler.deleteJob(JobKey.jobKey(scheduleId, JOB_GROUP));
            log.info("Unscheduled: {}", scheduleId);
        }

        return result;
    }

    /**
     * Pause a schedule.
     */
    public void pause(String scheduleId) throws SchedulerException {
        scheduler.pauseTrigger(TriggerKey.triggerKey(scheduleId, TRIGGER_GROUP));
        log.info("Paused schedule: {}", scheduleId);
    }

    /**
     * Resume a paused schedule.
     */
    public void resume(String scheduleId) throws SchedulerException {
        scheduler.resumeTrigger(TriggerKey.triggerKey(scheduleId, TRIGGER_GROUP));
        log.info("Resumed schedule: {}", scheduleId);
    }

    /**
     * Check if a schedule exists.
     */
    public boolean exists(String scheduleId) throws SchedulerException {
        return scheduler.checkExists(TriggerKey.triggerKey(scheduleId, TRIGGER_GROUP));
    }

    /**
     * Get the next fire time for a schedule.
     */
    public Date getNextFireTime(String scheduleId) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(scheduleId, TRIGGER_GROUP));
        return trigger != null ? trigger.getNextFireTime() : null;
    }

    /**
     * Trigger a scheduled workflow immediately (outside of its schedule).
     */
    public void triggerNow(String scheduleId) throws SchedulerException {
        scheduler.triggerJob(JobKey.jobKey(scheduleId, JOB_GROUP));
        log.info("Triggered schedule immediately: {}", scheduleId);
    }
}
