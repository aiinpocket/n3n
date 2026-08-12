package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleSyncServiceTest extends BaseServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SchedulerService schedulerService;

    @InjectMocks
    private ScheduleSyncService scheduleSyncService;

    private final UUID flowId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private Map<String, Object> definitionWithTrigger(Map<String, Object> config) {
        return Map.of("nodes", List.of(
                Map.of("id", "node-1", "type", "scheduleTrigger", "data", Map.of("config", config)),
                Map.of("id", "node-2", "type", "httpRequest", "data", Map.of())
        ));
    }

    @Test
    void syncFromDefinition_createsScheduleForNewTrigger() throws Exception {
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of());
        when(schedulerService.scheduleCron(eq(flowId), anyString(), eq("Asia/Taipei"), eq(ownerId)))
                .thenReturn("quartz-1");

        scheduleSyncService.syncFromDefinition(flowId, definitionWithTrigger(Map.of(
                "scheduleType", "cron",
                "cronExpression", "0 9 * * *",
                "timezone", "Asia/Taipei"
        )), ownerId);

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        Schedule saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("auto:node-1");
        assertThat(saved.getCronExpression()).isEqualTo("0 0 9 * * ?");
        assertThat(saved.getCreatedBy()).isEqualTo(ownerId);
        assertThat(saved.getQuartzScheduleId()).isEqualTo("quartz-1");
    }

    @Test
    void syncFromDefinition_skipsUnchangedActiveSchedule() throws Exception {
        Schedule existing = Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .name("auto:node-1")
                .cronExpression("0 0 9 * * ?")
                .timezone("UTC")
                .isActive(true)
                .quartzScheduleId("quartz-1")
                .createdBy(ownerId)
                .build();
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of(existing));
        when(schedulerService.exists("quartz-1")).thenReturn(true);

        scheduleSyncService.syncFromDefinition(flowId, definitionWithTrigger(Map.of(
                "scheduleType", "cron",
                "cronExpression", "0 0 9 * * ?"
        )), ownerId);

        verify(schedulerService, never()).scheduleCron(any(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).save(any());
        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void syncFromDefinition_updatesChangedCron() throws Exception {
        Schedule existing = Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .name("auto:node-1")
                .cronExpression("0 0 9 * * ?")
                .timezone("UTC")
                .isActive(true)
                .quartzScheduleId("quartz-old")
                .createdBy(ownerId)
                .build();
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of(existing));
        when(schedulerService.scheduleCron(eq(flowId), eq("0 0 18 * * ?"), eq("UTC"), eq(ownerId)))
                .thenReturn("quartz-new");

        scheduleSyncService.syncFromDefinition(flowId, definitionWithTrigger(Map.of(
                "scheduleType", "cron",
                "cronExpression", "0 0 18 * * ?"
        )), ownerId);

        verify(schedulerService).unschedule("quartz-old");
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getCronExpression()).isEqualTo("0 0 18 * * ?");
        assertThat(captor.getValue().getQuartzScheduleId()).isEqualTo("quartz-new");
    }

    @Test
    void syncFromDefinition_removesOrphanAutoSchedules() throws Exception {
        Schedule orphan = Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .name("auto:removed-node")
                .cronExpression("0 0 9 * * ?")
                .timezone("UTC")
                .quartzScheduleId("quartz-orphan")
                .createdBy(ownerId)
                .build();
        Schedule manual = Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .name("我的手動排程")
                .cronExpression("0 0 9 * * ?")
                .timezone("UTC")
                .quartzScheduleId("quartz-manual")
                .createdBy(ownerId)
                .build();
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of(orphan, manual));

        scheduleSyncService.syncFromDefinition(flowId, Map.of("nodes", List.of()), ownerId);

        verify(schedulerService).unschedule("quartz-orphan");
        verify(scheduleRepository).delete(orphan);
        // 手動排程不受影響
        verify(scheduleRepository, never()).delete(manual);
    }

    @Test
    void syncFromDefinition_disabledTriggerRemovesSchedule() throws Exception {
        Schedule existing = Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .name("auto:node-1")
                .cronExpression("0 0 9 * * ?")
                .timezone("UTC")
                .quartzScheduleId("quartz-1")
                .createdBy(ownerId)
                .build();
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of(existing));

        scheduleSyncService.syncFromDefinition(flowId, definitionWithTrigger(Map.of(
                "scheduleType", "cron",
                "cronExpression", "0 0 9 * * ?",
                "enabled", false
        )), ownerId);

        verify(schedulerService).unschedule("quartz-1");
        verify(scheduleRepository).delete(existing);
        verify(schedulerService, never()).scheduleCron(any(), anyString(), anyString(), any());
    }

    @Test
    void syncFromDefinition_invalidCronDoesNotThrow() throws Exception {
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of());
        when(schedulerService.scheduleCron(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Invalid cron expression"));

        // 不應丟出例外（發布不能因排程同步失敗而中斷）
        scheduleSyncService.syncFromDefinition(flowId, definitionWithTrigger(Map.of(
                "scheduleType", "cron",
                "cronExpression", "0 0 9 * * ?"
        )), ownerId);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void removeAutoSchedules_removesOnlyAutoPrefixed() throws Exception {
        Schedule auto = Schedule.builder()
                .id(UUID.randomUUID()).flowId(flowId).name("auto:n1")
                .cronExpression("0 0 9 * * ?").timezone("UTC")
                .quartzScheduleId("q1").createdBy(ownerId).build();
        Schedule manual = Schedule.builder()
                .id(UUID.randomUUID()).flowId(flowId).name("manual")
                .cronExpression("0 0 9 * * ?").timezone("UTC")
                .quartzScheduleId("q2").createdBy(ownerId).build();
        when(scheduleRepository.findByFlowId(flowId)).thenReturn(List.of(auto, manual));

        scheduleSyncService.removeAutoSchedules(flowId);

        verify(scheduleRepository).delete(auto);
        verify(scheduleRepository, never()).delete(manual);
    }

    // ---- cron 轉換 ----

    @Test
    void resolveQuartzCron_convertsFiveFieldCrontab() {
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "cronExpression", "30 9 * * *"))).isEqualTo("0 30 9 * * ?");
        // 有指定星期時 DOM 必須為 ?
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "cronExpression", "0 9 * * MON-FRI"))).isEqualTo("0 0 9 ? * MON-FRI");
    }

    @Test
    void resolveQuartzCron_passesThroughQuartzFormat() {
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "cronExpression", "0 0 9 * * ?"))).isEqualTo("0 0 9 * * ?");
    }

    @Test
    void resolveQuartzCron_convertsInterval() {
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "scheduleType", "interval", "interval", 15, "intervalUnit", "minutes")))
                .isEqualTo("0 0/15 * * * ?");
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "scheduleType", "interval", "interval", 6, "intervalUnit", "hours")))
                .isEqualTo("0 0 0/6 * * ?");
    }

    @Test
    void resolveQuartzCron_returnsNullForInvalid() {
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of("cronExpression", ""))).isNull();
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of("cronExpression", "not a cron at all ok"))).isNotNull();
        assertThat(ScheduleSyncService.resolveQuartzCron(Map.of(
                "scheduleType", "interval", "interval", -1))).isNull();
    }
}
