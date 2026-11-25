-- 实时运营绩效指标示例。
-- 具体状态枚举需按实际数据库方言调整；本视图不保存重复累计值。

CREATE VIEW "V_DENTIST_PERFORMANCE" AS
SELECT
    s."dentist_id",
    s."clinic_id",
    CAST(s."starts_at" AS DATE) AS metric_date,
    COUNT(DISTINCT a."appointment_id") AS appointment_count,
    COUNT(DISTINCT CASE WHEN e."status" IN ('ARRIVED', 'COMPLETED') THEN e."encounter_id" END) AS attended_count,
    COUNT(DISTINCT CASE WHEN a."status" = 'CANCELLED' THEN a."appointment_id" END) AS cancelled_count,
    COUNT(DISTINCT CASE WHEN a."status" = 'NO_SHOW' THEN a."appointment_id" END) AS no_show_count,
    COUNT(DISTINCT CASE WHEN e."status" = 'COMPLETED' THEN e."encounter_id" END) AS completed_count,
    COUNT(t."treatment_line_id") AS treatment_count
FROM "SHIFT" s
LEFT JOIN "APPOINTMENT" a ON a."shift_id" = s."shift_id"
LEFT JOIN "ENCOUNTER" e ON e."appointment_id" = a."appointment_id"
LEFT JOIN "TREATMENT_LINE" t ON t."encounter_id" = e."encounter_id"
WHERE s."status" = 'PUBLISHED'
GROUP BY s."dentist_id", s."clinic_id", CAST(s."starts_at" AS DATE);

