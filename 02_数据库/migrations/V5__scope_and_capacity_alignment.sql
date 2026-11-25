-- =====================================================================
-- ADC 牙科诊所 V5：确认三项口径并落地
--   1) 医生可见范围统一（dentist_* 一律 CLINIC + 归属门店，取消历史 CHAIN 例外）
--   2) 号源容量口径：每"诊所 × 日期 × 时段" 3 个名额（非每位医生 3 个）——核对现状
--   3) 利润口径：营业额 − 耗材成本 ——核对现状
-- =====================================================================

BEGIN;

-- 一、医生可见范围统一为 CLINIC + 本人归属门店
UPDATE "ACCESS_GRANT" g
   SET scope_type = 'CLINIC',
       clinic_id  = s.home_clinic_id
  FROM "ACCOUNT" a
  JOIN "STAFF"  s ON s.staff_id = a.staff_id
 WHERE g.account_id = a.account_id
   AND g.role_code  = 'DENTIST'
   AND (g.scope_type <> 'CLINIC' OR g.clinic_id IS DISTINCT FROM s.home_clinic_id);

-- 二、前台同样强制 CLINIC + 归属门店（防历史不一致）
UPDATE "ACCESS_GRANT" g
   SET scope_type = 'CLINIC',
       clinic_id  = s.home_clinic_id
  FROM "ACCOUNT" a
  JOIN "STAFF"  s ON s.staff_id = a.staff_id
 WHERE g.account_id = a.account_id
   AND g.role_code  = 'RECEPTION'
   AND (g.scope_type <> 'CLINIC' OR g.clinic_id IS DISTINCT FROM s.home_clinic_id);

-- 三、患者一律 SELF、无门店
UPDATE "ACCESS_GRANT"
   SET scope_type = 'SELF', clinic_id = NULL
 WHERE role_code = 'PATIENT'
   AND (scope_type <> 'SELF' OR clinic_id IS NOT NULL);

-- 四、管理层一律 CHAIN、无门店（V4 已收敛，此处兜底）
UPDATE "ACCESS_GRANT"
   SET scope_type = 'CHAIN', clinic_id = NULL
 WHERE role_code = 'MANAGER'
   AND (scope_type <> 'CHAIN' OR clinic_id IS NOT NULL);

-- 五、用约束固化「角色 → 数据范围」矩阵，防止以后再出现例外
ALTER TABLE "ACCESS_GRANT" DROP CONSTRAINT IF EXISTS ck_access_grant_scope_matrix;
ALTER TABLE "ACCESS_GRANT"
  ADD CONSTRAINT ck_access_grant_scope_matrix CHECK (
        (role_code = 'PATIENT'   AND scope_type = 'SELF'  AND clinic_id IS NULL)
     OR (role_code = 'RECEPTION' AND scope_type = 'CLINIC' AND clinic_id IS NOT NULL)
     OR (role_code = 'DENTIST'   AND scope_type = 'CLINIC' AND clinic_id IS NOT NULL)
     OR (role_code = 'MANAGER'   AND scope_type = 'CHAIN'  AND clinic_id IS NULL)
  );

COMMIT;

-- =====================================================================
-- 口径核对（只读）
-- =====================================================================

-- 2) 容量口径：BOOKING_SLOT 唯一键应为 (clinic_id, slot_date, slot_code)，
--    且表中不应有任何 staff/dentist 维度
SELECT indexdef
  FROM pg_indexes
 WHERE tablename = 'BOOKING_SLOT'
   AND indexdef ILIKE '%UNIQUE%';

SELECT count(*) AS has_staff_dimension
  FROM information_schema.columns
 WHERE table_name = 'BOOKING_SLOT'
   AND column_name ILIKE '%staff%' OR column_name ILIKE '%dentist%';

-- 各诊所各时段容量一览（应为统一的 3）
SELECT clinic_id, slot_code, min(capacity) AS min_cap, max(capacity) AS max_cap
  FROM "BOOKING_SLOT"
 GROUP BY clinic_id, slot_code
 ORDER BY clinic_id, slot_code;

-- 3) 利润口径：profit = revenue - cost
SELECT clinic_name, biz_date, revenue, cost, profit, (revenue - cost) AS check_profit
  FROM "V_CLINIC_DASHBOARD"
 WHERE revenue > 0 OR cost > 0
 ORDER BY biz_date;

-- 1) 医生可见范围核对
SELECT g.role_code, g.scope_type, g.clinic_id, count(*) AS accounts
  FROM "ACCESS_GRANT" g
 GROUP BY g.role_code, g.scope_type, g.clinic_id
 ORDER BY g.role_code, g.scope_type, g.clinic_id;
