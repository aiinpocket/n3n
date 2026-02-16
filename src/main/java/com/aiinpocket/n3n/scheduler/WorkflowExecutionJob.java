package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Quartz job that triggers workflow execution.
 */
@Component
@Slf4j
public class WorkflowExecutionJob implements Job {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String flowId = dataMap.getString("flowId");
        String userId = dataMap.getString("userId");
        String scheduleId = dataMap.getString("scheduleId");

        log.info("Scheduled execution triggered: flowId={}, scheduleId={}", flowId, scheduleId);

        try {
            // Get ExecutionService from Spring context
            // We use lazy lookup to avoid circular dependencies
            Object executionService = applicationContext.getBean("executionService");

            // Prepare trigger data
            Map<String, Object> triggerData = Map.of(
                "triggeredBy", "schedule",
                "scheduleId", scheduleId,
                "scheduledTime", context.getScheduledFireTime(),
                "actualFireTime", context.getFireTime()
            );

            // Call startExecution via reflection to avoid compile-time dependency
            java.lang.reflect.Method startMethod = executionService.getClass()
                .getMethod("startExecution", UUID.class, UUID.class, Map.class);

            Object result = startMethod.invoke(executionService,
                UUID.fromString(flowId),
                UUID.fromString(userId),
                triggerData);

            log.info("Scheduled execution completed for flow {}: {}", flowId, result);

            // Update lastRunAt on the Schedule entity (lookup by Quartz schedule ID, not DB primary key)
            try {
                scheduleRepository.findByQuartzScheduleId(scheduleId).ifPresent(schedule -> {
                    schedule.setLastRunAt(Instant.now());
                    scheduleRepository.save(schedule);
                });
            } catch (Exception updateErr) {
                log.warn("Failed to update lastRunAt for schedule {}: {}", scheduleId, updateErr.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to execute scheduled workflow {}: {}", flowId, e.getClass().getSimpleName(), e);
            // Log but do not rethrow - rethrowing JobExecutionException can cause Quartz
            // to disable the trigger, preventing future scheduled executions
        }
    }
}
