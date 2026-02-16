package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Recovers active schedules from database on application startup.
 * Quartz uses in-memory RAMJobStore, so all jobs are lost on restart.
 * This service re-registers them from the persistent Schedule entities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleRecoveryService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulerService schedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverSchedules() {
        List<Schedule> activeSchedules = scheduleRepository.findByIsActiveTrue();

        if (activeSchedules.isEmpty()) {
            log.info("SCHEDULE_RECOVERY no active schedules to recover");
            return;
        }

        int recovered = 0;
        int failed = 0;

        for (Schedule schedule : activeSchedules) {
            try {
                String quartzId = schedulerService.scheduleCron(
                        schedule.getFlowId(),
                        schedule.getCronExpression(),
                        schedule.getTimezone(),
                        schedule.getCreatedBy()
                );
                schedule.setQuartzScheduleId(quartzId);
                scheduleRepository.save(schedule);
                recovered++;
            } catch (Exception e) {
                failed++;
                log.warn("SCHEDULE_RECOVERY_FAILED id={} flow={} cron={}: {}",
                        schedule.getId(), schedule.getFlowId(),
                        schedule.getCronExpression(), e.getMessage());
            }
        }

        log.info("SCHEDULE_RECOVERY total={} recovered={} failed={}",
                activeSchedules.size(), recovered, failed);
    }
}
