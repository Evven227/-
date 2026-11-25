-- =====================================================================
-- ADC 连锁牙科诊所 数据库调整（V3）— 第三部分
-- 轮班生成 / 约束与触发器 / 演示调班与预约 / 绩效规则 / 看板视图
-- 幂等，可重复执行。
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 五、归档窗口内的旧演示排班（同一人同一天只允许一条生效排班）
-- ---------------------------------------------------------------------
UPDATE "SHIFT" SET status='SUPERSEDED'
WHERE status='PUBLISHED'
  AND starts_at::date BETWEEN DATE '2026-10-26' AND DATE '2026-11-08';

UPDATE "SHIFT" s
   SET staff_id = s.dentist_id,
       role_type = 'DENTIST',
       shift_date = s.starts_at::date
WHERE s.staff_id IS NULL AND s.dentist_id IS NOT NULL;

UPDATE "SHIFT" s SET clinic_id = p.clinic_id
FROM (SELECT dentist_id, MIN(clinic_id) clinic_id FROM "PRACTICE" WHERE status='ACTIVE' GROUP BY dentist_id) p
WHERE s.dentist_id = p.dentist_id AND s.clinic_id IS NULL;

-- ---------------------------------------------------------------------
-- 6.0 排班覆盖前台后 dentist_id 可为 NULL（前台非医生）
ALTER TABLE "SHIFT" ALTER COLUMN dentist_id DROP NOT NULL;

-- 6.1 按轮班周期生成未来排班（医生每天 3 人、前台每天 1 人）
DO $gen$
DECLARE
  d date; cid int; rm record; start_seq int;
BEGIN
  FOR d IN SELECT gs::date FROM generate_series(DATE '2026-10-26', DATE '2026-11-08', INTERVAL '1 day') gs LOOP
    FOR cid IN 1..2 LOOP
      FOR rm IN
        SELECT c.cycle_id, m.staff_id, m.seq_no, c.cycle_length, c.daily_headcount, c.role_type, c.anchor_date
        FROM "ROSTER_MEMBER" m
        JOIN "ROSTER_CYCLE"  c ON c.cycle_id = m.cycle_id
        WHERE c.clinic_id = cid AND c.active
        ORDER BY c.role_type, m.seq_no
      LOOP
        start_seq := ((d - rm.anchor_date) % rm.cycle_length + rm.cycle_length) % rm.cycle_length;
        IF ((rm.seq_no - start_seq + rm.cycle_length) % rm.cycle_length) < rm.daily_headcount THEN
          IF NOT EXISTS (SELECT 1 FROM "SHIFT" x
                         WHERE x.shift_series_no = 'ROS-' || cid || '-' || to_char(d,'YYYYMMDD') || '-' || rm.staff_id) THEN
            INSERT INTO "SHIFT"(shift_series_no, dentist_id, staff_id, clinic_id, role_type, shift_date,
                                starts_at, ends_at, status, version_no, source, published_by_staff_id, published_at)
            VALUES ('ROS-' || cid || '-' || to_char(d,'YYYYMMDD') || '-' || rm.staff_id,
                    CASE WHEN rm.role_type='DENTIST' THEN rm.staff_id ELSE NULL END,
                    rm.staff_id, cid, rm.role_type, d,
                    d + TIME '08:00', d + TIME '21:00', 'PUBLISHED', 1, 'ROSTER',
                    CASE WHEN cid=1 THEN 2 ELSE 4 END, now());
          END IF;
        END IF;
      END LOOP;
    END LOOP;
  END LOOP;
END $gen$;

-- ---------------------------------------------------------------------
-- 七、展开每日号源（每诊所每段 3 个名额）
-- ---------------------------------------------------------------------
INSERT INTO "BOOKING_SLOT"(clinic_id, slot_date, slot_code, capacity, status)
SELECT t.clinic_id, gs::date, t.slot_code, t.capacity, 'OPEN'
FROM "SLOT_TEMPLATE" t
CROSS JOIN generate_series(DATE '2026-10-27', DATE '2026-11-09', INTERVAL '1 day') gs
WHERE t.active
ON CONFLICT (clinic_id, slot_date, slot_code) DO NOTHING;

-- 既有预约回填 clinic_id / slot_code（保持历史数据完整）
UPDATE "APPOINTMENT" a SET clinic_id = s.clinic_id
FROM "SHIFT" s WHERE a.shift_id = s.shift_id AND a.clinic_id IS NULL;

-- ---------------------------------------------------------------------
-- 八、约束与触发器
-- ---------------------------------------------------------------------
-- 8.1 同一人同一天只能有一条生效排班（跨店冲突一并禁止）
CREATE UNIQUE INDEX IF NOT EXISTS ux_shift_staff_day
  ON "SHIFT"(staff_id, shift_date) WHERE status = 'PUBLISHED';

-- 8.2 同一患者在重叠时段只能有一条有效预约（跨店一并禁止）
CREATE INDEX IF NOT EXISTS ix_appt_clinic_slot ON "APPOINTMENT"(clinic_id, starts_at, slot_code);
DO $ex$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ex_appt_patient_overlap') THEN
    ALTER TABLE "APPOINTMENT" ADD CONSTRAINT ex_appt_patient_overlap
      EXCLUDE USING gist (patient_id WITH =, tsrange(starts_at, ends_at, '[)') WITH &&)
      WHERE (status IN ('BOOKED','ARRIVED'));
  END IF;
END $ex$;

-- 8.3 同一患者同一诊所同一时段不可重复预约
CREATE UNIQUE INDEX IF NOT EXISTS ux_appt_patient_slot
  ON "APPOINTMENT"(patient_id, clinic_id, slot_code, starts_at)
  WHERE status IN ('BOOKED','ARRIVED','COMPLETED') AND slot_code IS NOT NULL;

-- 8.4 时段容量硬约束（满 3 人拒绝；取消/改约后立即释放）
CREATE OR REPLACE FUNCTION fn_slot_capacity_guard() RETURNS trigger AS $fn$
DECLARE
  cap int; used int;
BEGIN
  IF NEW.status IN ('BOOKED','ARRIVED','COMPLETED')
     AND NEW.clinic_id IS NOT NULL AND NEW.slot_code IS NOT NULL THEN
    SELECT capacity INTO cap
      FROM "BOOKING_SLOT"
     WHERE clinic_id = NEW.clinic_id AND slot_date = NEW.starts_at::date AND slot_code = NEW.slot_code;
    IF cap IS NULL THEN
      RETURN NEW;
    END IF;
    SELECT count(*) INTO used
      FROM "APPOINTMENT"
     WHERE clinic_id = NEW.clinic_id
       AND slot_code = NEW.slot_code
       AND starts_at::date = NEW.starts_at::date
       AND status IN ('BOOKED','ARRIVED','COMPLETED')
       AND appointment_id <> COALESCE(NEW.appointment_id, -1);
    IF used >= cap THEN
      RAISE EXCEPTION '该时段已约满（诊所=% 日期=% 时段=% 容量=%）', NEW.clinic_id, NEW.starts_at::date, NEW.slot_code, cap;
    END IF;
  END IF;
  RETURN NEW;
END $fn$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_appt_slot_capacity ON "APPOINTMENT";
CREATE TRIGGER trg_appt_slot_capacity
BEFORE INSERT OR UPDATE ON "APPOINTMENT"
FOR EACH ROW EXECUTE FUNCTION fn_slot_capacity_guard();

COMMIT;
