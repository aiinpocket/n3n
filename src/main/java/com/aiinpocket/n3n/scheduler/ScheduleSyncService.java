package com.aiinpocket.n3n.scheduler;

import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 流程發布時自動同步 scheduleTrigger 節點為實際的 Quartz 排程。
 * <p>
 * 目標使用者是非技術背景的 BA：AI 生成的流程若含排程觸發節點，
 * 發布後即自動掛載定時執行，不需再到排程頁手動建立。
 * <p>
 * 自動管理的排程以名稱前綴 {@link #AUTO_NAME_PREFIX} + nodeId 識別，
 * 與使用者手動建立的排程互不干擾。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleSyncService {

    public static final String AUTO_NAME_PREFIX = "auto:";
    private static final String SCHEDULE_TRIGGER_TYPE = "scheduleTrigger";

    private final ScheduleRepository scheduleRepository;
    private final SchedulerService schedulerService;

    /**
     * 依據發布中的流程定義同步自動排程：
     * 新增的 scheduleTrigger 建立排程、設定變更的更新排程、已移除或停用的節點刪除排程。
     * 個別節點同步失敗（如 cron 無效）只記 warning，不會讓發布失敗。
     */
    @Transactional
    public void syncFromDefinition(UUID flowId, Map<String, Object> definition, UUID ownerId) {
        Map<String, Map<String, Object>> desired = extractScheduleTriggers(definition);

        List<Schedule> existingAuto = scheduleRepository.findByFlowId(flowId).stream()
                .filter(s -> s.getName() != null && s.getName().startsWith(AUTO_NAME_PREFIX))
                .toList();
        Map<String, Schedule> existingByNodeId = new HashMap<>();
        for (Schedule s : existingAuto) {
            existingByNodeId.put(s.getName().substring(AUTO_NAME_PREFIX.length()), s);
        }

        // 建立或更新目前定義中的排程觸發節點
        for (Map.Entry<String, Map<String, Object>> entry : desired.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> config = entry.getValue();
            try {
                syncOne(flowId, ownerId, nodeId, config, existingByNodeId.remove(nodeId));
            } catch (Exception e) {
                log.warn("Auto-schedule sync failed for flow {} node {}: {}", flowId, nodeId, e.getMessage());
            }
        }

        // 定義中已不存在的節點 → 移除對應的自動排程
        for (Schedule orphan : existingByNodeId.values()) {
            removeSchedule(orphan);
        }
    }

    /** 流程被取消發布/停用時移除其所有自動排程（手動排程不受影響）。 */
    @Transactional
    public void removeAutoSchedules(UUID flowId) {
        scheduleRepository.findByFlowId(flowId).stream()
                .filter(s -> s.getName() != null && s.getName().startsWith(AUTO_NAME_PREFIX))
                .forEach(this::removeSchedule);
    }

    private void syncOne(UUID flowId, UUID ownerId, String nodeId,
                         Map<String, Object> config, Schedule existing) throws SchedulerException {
        boolean enabled = !Boolean.FALSE.equals(config.get("enabled"));
        String cron = resolveQuartzCron(config);
        String timezone = stringValue(config.get("timezone"), "UTC");

        if (!enabled || cron == null) {
            if (existing != null) {
                removeSchedule(existing);
            }
            if (enabled) {
                log.warn("scheduleTrigger node {} in flow {} has no valid schedule config, skipped", nodeId, flowId);
            }
            return;
        }

        Map<String, Object> input = config.get("payload") instanceof Map<?, ?> payload
                ? Map.of("payload", payload)
                : null;

        if (existing != null
                && cron.equals(existing.getCronExpression())
                && timezone.equals(existing.getTimezone())
                && Boolean.TRUE.equals(existing.getIsActive())
                && existing.getQuartzScheduleId() != null
                && schedulerService.exists(existing.getQuartzScheduleId())) {
            return; // 設定未變且 Quartz job 存活，不需動作
        }

        // 先註冊新 job（順便驗證 cron），成功後再清掉舊 job
        String quartzId = schedulerService.scheduleCron(flowId, cron, timezone, ownerId);
        Instant nextRunAt = nextRunInstant(quartzId);

        if (existing != null) {
            unscheduleQuietly(existing.getQuartzScheduleId());
            existing.setCronExpression(cron);
            existing.setTimezone(timezone);
            existing.setInput(input);
            existing.setIsActive(true);
            existing.setQuartzScheduleId(quartzId);
            existing.setNextRunAt(nextRunAt);
            scheduleRepository.save(existing);
            log.info("Auto-schedule updated: flow={}, node={}, cron='{}'", flowId, nodeId, cron);
        } else {
            Schedule schedule = Schedule.builder()
                    .flowId(flowId)
                    .name(AUTO_NAME_PREFIX + nodeId)
                    .cronExpression(cron)
                    .timezone(timezone)
                    .input(input)
                    .nextRunAt(nextRunAt)
                    .createdBy(ownerId)
                    .quartzScheduleId(quartzId)
                    .build();
            scheduleRepository.save(schedule);
            log.info("Auto-schedule created: flow={}, node={}, cron='{}'", flowId, nodeId, cron);
        }
    }

    private void removeSchedule(Schedule schedule) {
        unscheduleQuietly(schedule.getQuartzScheduleId());
        scheduleRepository.delete(schedule);
        log.info("Auto-schedule removed: id={}, name={}", schedule.getId(), schedule.getName());
    }

    private void unscheduleQuietly(String quartzScheduleId) {
        if (quartzScheduleId == null) {
            return;
        }
        try {
            schedulerService.unschedule(quartzScheduleId);
        } catch (SchedulerException e) {
            log.warn("Failed to unschedule quartz job {}: {}", quartzScheduleId, e.getMessage());
        }
    }

    private Instant nextRunInstant(String quartzScheduleId) {
        try {
            Date next = schedulerService.getNextFireTime(quartzScheduleId);
            return next != null ? next.toInstant() : null;
        } catch (SchedulerException e) {
            return null;
        }
    }

    /** 從流程定義中取出所有 scheduleTrigger 節點的 config，鍵為 nodeId。 */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractScheduleTriggers(Map<String, Object> definition) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (definition == null || !(definition.get("nodes") instanceof List<?> nodes)) {
            return result;
        }
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> node)) continue;
            if (!SCHEDULE_TRIGGER_TYPE.equals(node.get("type"))) continue;
            Object id = node.get("id");
            if (id == null) continue;
            Map<String, Object> config = new HashMap<>();
            if (node.get("data") instanceof Map<?, ?> data
                    && data.get("config") instanceof Map<?, ?> cfg) {
                config = new HashMap<>((Map<String, Object>) cfg);
            }
            result.put(id.toString(), config);
        }
        return result;
    }

    /**
     * 將節點設定轉為 Quartz cron。支援：
     * <ul>
     *   <li>cron 型：5 欄位（分 時 日 月 週，n8n/crontab 風格）自動補秒並修正 DOM/DOW 衝突；6/7 欄位直接使用</li>
     *   <li>interval 型：轉為等效 cron（如每 15 分 → "0 0/15 * * * ?"）</li>
     * </ul>
     * 無法轉換時回傳 null。
     */
    static String resolveQuartzCron(Map<String, Object> config) {
        String scheduleType = stringValue(config.get("scheduleType"), "cron");

        if ("interval".equals(scheduleType)) {
            return intervalToCron(config);
        }

        String cron = stringValue(config.get("cronExpression"), "").trim();
        if (cron.isEmpty()) {
            return null;
        }
        String[] fields = cron.split("\\s+");
        if (fields.length == 5) {
            // crontab 風格 → Quartz：補秒欄位，且 DOM/DOW 必須有一邊為 '?'
            String dom = fields[2];
            String dow = fields[4];
            if (!"*".equals(dow) && !"?".equals(dow)) {
                dom = "?".equals(dom) ? "?" : ("*".equals(dom) ? "?" : dom);
                if (!"?".equals(dom)) {
                    // DOM 與 DOW 都有值時 Quartz 不支援，以 DOW 為準
                    dom = "?";
                }
            } else {
                dow = "?";
            }
            return String.join(" ", "0", fields[0], fields[1], dom, fields[3], dow);
        }
        if (fields.length == 6 || fields.length == 7) {
            return cron;
        }
        return null;
    }

    private static String intervalToCron(Map<String, Object> config) {
        int interval;
        try {
            Object raw = config.get("interval");
            interval = raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
        if (interval < 1) {
            return null;
        }
        String unit = stringValue(config.get("intervalUnit"), "minutes");
        return switch (unit) {
            case "seconds" -> interval < 60 ? "0/" + interval + " * * * * ?" : null;
            case "minutes" -> interval < 60 ? "0 0/" + interval + " * * * ?" : null;
            case "hours" -> interval < 24 ? "0 0 0/" + interval + " * * ?" : null;
            case "days" -> "0 0 0 1/" + interval + " * ?";
            default -> null;
        };
    }

    private static String stringValue(Object value, String defaultValue) {
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : defaultValue;
    }
}
