-- =====================================================================
-- ADC 牙科诊所 V4：角色体系收敛 —— 取消「店长」，统一为「管理层」
-- 目标：不区分单店店长与总部管理员，两类合并为同一角色 MANAGER，
--       数据范围统一为 CHAIN（可查看/管理两家诊所全量数据）。
-- =====================================================================

BEGIN;

-- 一、员工角色：CLINIC_MANAGER(店长) -> MANAGER(管理层)
--     管理层不再按店划分，home_clinic_id 置空（归属连锁总部）
UPDATE "STAFF"
   SET role_type = 'MANAGER',
       home_clinic_id = NULL
 WHERE role_type = 'CLINIC_MANAGER';

UPDATE "STAFF"
   SET home_clinic_id = NULL
 WHERE role_type = 'MANAGER' AND home_clinic_id IS NOT NULL;

-- 二、账号登录名统一前缀（密码不变）
UPDATE "ACCOUNT" SET login_name = 'manager_a'  WHERE login_name = 'clinic_manager_a';
UPDATE "ACCOUNT" SET login_name = 'manager_b'  WHERE login_name = 'clinic_manager_b';

-- 三、先摘除旧字典，再迁移数据，最后重建字典
--     （顺序不能颠倒：旧字典不允许 MANAGER，新字典不允许 CLINIC_MANAGER）
ALTER TABLE "ACCESS_GRANT" DROP CONSTRAINT IF EXISTS ck_access_grant_role_code;

DO $$
DECLARE c record;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = '"STAFF"'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) ILIKE '%role_type%' LOOP
    EXECUTE format('ALTER TABLE "STAFF" DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

-- 四、授权迁移：店长 CLINIC 范围 -> 管理层 CHAIN 范围；HQ_MANAGER -> MANAGER
UPDATE "ACCESS_GRANT"
   SET role_code  = 'MANAGER',
       scope_type = 'CHAIN',
       clinic_id  = NULL
 WHERE role_code IN ('CLINIC_MANAGER', 'HQ_MANAGER');

-- 五、重建字典：PATIENT / RECEPTION / DENTIST / MANAGER
ALTER TABLE "ACCESS_GRANT"
  ADD CONSTRAINT ck_access_grant_role_code
  CHECK (role_code IN ('PATIENT', 'RECEPTION', 'DENTIST', 'MANAGER'));

ALTER TABLE "STAFF"
  ADD CONSTRAINT ck_staff_role_type
  CHECK (role_type IN ('DENTIST', 'RECEPTION', 'MANAGER'));

-- 六、为 STAFF.role_type 建索引（按角色筛选员工常用）
CREATE INDEX IF NOT EXISTS ix_staff_role_type ON "STAFF"(role_type);

COMMIT;

-- =====================================================================
-- 校验
-- =====================================================================
SELECT s.staff_id, s.staff_no, s.name, s.role_type, s.home_clinic_id,
       a.login_name, g.role_code, g.scope_type, g.clinic_id
  FROM "STAFF" s
  LEFT JOIN "ACCOUNT" a      ON a.staff_id = s.staff_id
  LEFT JOIN "ACCESS_GRANT" g ON g.account_id = a.account_id
 WHERE s.role_type = 'MANAGER'
 ORDER BY s.staff_id;
