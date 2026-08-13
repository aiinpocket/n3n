-- 回填執行相關活動的流程名稱：
-- 早期版本的 EXECUTION_* 活動只把 flowId 存在 details，resource_name 一律為 NULL，
-- 導致儀表板「最近活動」與活動記錄頁顯示「-」。以 details.flowId 關聯 flows 補上名稱。
-- （流程已被刪除的舊活動找不到名稱，維持 NULL。）
UPDATE user_activities ua
SET resource_name = f.name
FROM flows f
WHERE ua.resource_name IS NULL
  AND ua.activity_type IN ('EXECUTION_START', 'EXECUTION_COMPLETE', 'EXECUTION_FAIL', 'EXECUTION_CANCEL')
  AND (ua.details ->> 'flowId') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  AND f.id = (ua.details ->> 'flowId')::uuid;
