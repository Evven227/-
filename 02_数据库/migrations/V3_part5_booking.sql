-- =====================================================================
-- ADC 连锁牙科诊所 数据库调整（V3）— 第五部分
-- 演示预约/提醒、绩效规则与数据、看板视图、Flyway 登记
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 十一、演示预约（含一个满员时段，用于演示"已约满"）
-- ---------------------------------------------------------------------
DO $bk$
DECLARE
  r record; n int; sid bigint; st_time time; en_time time; acct bigint; caps int;
BEGIN
  FOR r IN SELECT * FROM (VALUES
      (1, DATE '2026-10-27', 'AM1', 1), (1, DATE '2026-10-27', 'AM1', 2), (1, DATE '2026-10-27', 'AM1', 3),
      (1, DATE '2026-10-27', 'AM2', 4), (1, DATE '2026-10-27', 'PM1', 5),
      (1, DATE '2026-10-28', 'PM2', 6), (1, DATE '2026-10-29', 'EV1', 7), (1, DATE '2026-10-30', 'AM1', 8),
      (2, DATE '2026-10-27', 'AM1', 9), (2, DATE '2026-10-27', 'AM1',10),
      (2, DATE '2026-10-28', 'PM1',11), (2, DATE '2026-10-29', 'EV1',12), (2, DATE '2026-10-31', 'AM1',13)
    ) v(clinic_id, slot_date, slot_code, patient_id)
  LOOP
    SELECT capacity INTO caps FROM "BOOKING_SLOT"
      WHERE clinic_id=r.clinic_id AND slot_date=r.slot_date AND slot_code=r.slot_code;
    SELECT count(*) INTO n FROM "APPOINTMENT"
      WHERE clinic_id=r.clinic_id AND slot_code=r.slot_code AND starts_at::date=r.slot_date
        AND status IN ('BOOKED','ARRIVED','COMPLETED');
    CONTINUE WHEN caps IS NULL OR n >= caps;   -- 满员则跳过，保证不越界

    SELECT t.start_time, t.end_time INTO st_time, en_time
      FROM "SLOT_TEMPLATE" t WHERE t.clinic_id=r.clinic_id AND t.slot_code=r.slot_code;

    SELECT q.shift_id INTO sid FROM (
      SELECT s.shift_id,
             ROW_NUMBER() OVER (ORDER BY (s.source='SUBSTITUTE'), s.staff_id) rn,
             count(*) OVER () tot
      FROM "SHIFT" s
      WHERE s.clinic_id=r.clinic_id AND s.shift_date=r.slot_date
        AND s.role_type='DENTIST' AND s.status='PUBLISHED'
    ) q WHERE q.rn = (n % q.tot) + 1;

    SELECT a.account_id INTO acct FROM "ACCOUNT" a WHERE a.patient_id = r.patient_id LIMIT 1;
    IF acct IS NULL THEN
      SELECT g.account_id INTO acct FROM "ACCESS_GRANT" g
       WHERE g.role_code='RECEPTION' AND g.clinic_id = r.clinic_id
       ORDER BY g.account_id LIMIT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM "APPOINTMENT" x
                   WHERE x.patient_id=r.patient_id AND x.clinic_id=r.clinic_id
                     AND x.slot_code=r.slot_code AND x.starts_at::date=r.slot_date
                     AND x.status IN ('BOOKED','ARRIVED','COMPLETED')) THEN
      INSERT INTO "APPOINTMENT"(patient_id, shift_id, clinic_id, slot_code, booking_no,
                                starts_at, ends_at, status, created_by_account_id, created_at)
      VALUES (r.patient_id, sid, r.clinic_id, r.slot_code,
              'BK' || to_char(r.slot_date,'YYYYMMDD') || '-C' || r.clinic_id || '-' || r.slot_code || '-P' || r.patient_id,
              r.slot_date + st_time, r.slot_date + en_time, 'BOOKED', acct, now());
    END IF;
  END LOOP;
END $bk$;

-- ---------------------------------------------------------------------
-- 十二、就诊提醒（开始前 12 小时）
-- ---------------------------------------------------------------------
-- 就诊前 12 小时触发的提醒（已存在则跳过）

INSERT INTO "NOTIFICATION"(patient_id, account_id, channel, event_type, title, body,
                           ref_type, ref_id, status, scheduled_at, created_at)
SELECT a.patient_id, ac.account_id, 'INAPP', 'APPT_REMINDER_12H',
       '就诊提醒：' || c.name || ' ' || t.slot_name,
       '您预约了 ' || to_char(a.starts_at::date,'YYYY-MM-DD') || ' ' || t.slot_name
         || '（' || to_char(t.start_time,'HH24:MI') || '-' || to_char(t.end_time,'HH24:MI') || '），请提前 15 分钟到店签到。',
       'APPOINTMENT', a.appointment_id,
       CASE WHEN (a.starts_at - INTERVAL '12 hours') < now() THEN 'SENT' ELSE 'PENDING' END,
       (a.starts_at - INTERVAL '12 hours')::timestamptz, now()
FROM "APPOINTMENT" a
JOIN "CLINIC" c ON c.clinic_id = a.clinic_id
JOIN "SLOT_TEMPLATE" t ON t.clinic_id = a.clinic_id AND t.slot_code = a.slot_code
LEFT JOIN "ACCOUNT" ac ON ac.patient_id = a.patient_id
WHERE a.status IN ('BOOKED','ARRIVED') AND a.slot_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM "NOTIFICATION" n
                  WHERE n.event_type='APPT_REMINDER_12H' AND n.ref_id = a.appointment_id);

-- ---------------------------------------------------------------------
-- 十三、绩效规则与当月绩效
-- ---------------------------------------------------------------------
INSERT INTO "PERFORMANCE_RULE"(rule_code, rule_name, rule_type, value, unit, effective_from, active) VALUES
 ('FULL_ATTENDANCE_BONUS','满勤奖励（无请假、无申请调班）','ATTENDANCE_BONUS',300,'CNY',DATE '2026-10-01',true),
 ('LEAVE_DAY_PENALTY','请假扣款（每天）','LEAVE_PENALTY',100,'CNY',DATE '2026-10-01',true),
 ('COVER_SHIFT_BONUS','顶班补贴（每次）','COVER_BONUS',80,'CNY',DATE '2026-10-01',true)
ON CONFLICT (rule_code) DO UPDATE SET value=EXCLUDED.value, rule_name=EXCLUDED.rule_name;

-- 13.1 补齐 10 月绩效主记录
INSERT INTO "DENTIST_PERFORMANCE"(dentist_id, clinic_id, period_start, period_end,
                                  appointment_count, attended_count, cancelled_count, no_show_count,
                                  completed_count, treatment_count, workload_target_count,
                                  performance_score, reward_level, review_status,
                                  calculation_version, calculated_at, created_at)
SELECT d.dentist_id, p.clinic_id, DATE '2026-10-01', DATE '2026-10-31',
       0,0,0,0,0,0,0, 0, 'NONE', 'DRAFT', 'v3', now(), now()
FROM "DENTIST" d
JOIN (SELECT dentist_id, MIN(clinic_id) clinic_id FROM "PRACTICE" WHERE status='ACTIVE' GROUP BY dentist_id) p
  ON p.dentist_id = d.dentist_id
WHERE NOT EXISTS (SELECT 1 FROM "DENTIST_PERFORMANCE" x
                  WHERE x.dentist_id = d.dentist_id
                    AND x.period_start <= DATE '2026-10-31' AND x.period_end >= DATE '2026-10-01');

-- 13.2 按既有排班/申请重算满勤、请假、顶班三项
UPDATE "DENTIST_PERFORMANCE" dp SET
  scheduled_days = q.scheduled_days,
  leave_days     = q.leave_days,
  swap_applied_count = q.swap_applied_count,
  cover_shift_count  = q.cover_shift_count,
  calculated_at  = now()
FROM (
  SELECT dp2.performance_id,
         COALESCE(t_sched.cnt,0) AS scheduled_days,
         COALESCE(t_leave.cnt,0) AS leave_days,
         COALESCE(t_swap.cnt,0)  AS swap_applied_count,
         COALESCE(t_cover.cnt,0) AS cover_shift_count
  FROM "DENTIST_PERFORMANCE" dp2
  LEFT JOIN (SELECT s.dentist_id, count(*) cnt FROM "SHIFT" s
              WHERE s.role_type='DENTIST' AND s.status='PUBLISHED' AND s.dentist_id IS NOT NULL
                AND s.shift_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
              GROUP BY s.dentist_id) t_sched ON t_sched.dentist_id = dp2.dentist_id
  LEFT JOIN (SELECT r.applicant_dentist_id dentist_id, count(*) cnt FROM "SHIFT_REQUEST" r
              WHERE r.request_type='LEAVE' AND r.status='IMPLEMENTED'
                AND r.requested_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
              GROUP BY r.applicant_dentist_id) t_leave ON t_leave.dentist_id = dp2.dentist_id
  LEFT JOIN (SELECT r.applicant_dentist_id dentist_id, count(*) cnt FROM "SHIFT_REQUEST" r
              WHERE r.request_type='SHIFT_CHANGE'
                AND r.requested_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
              GROUP BY r.applicant_dentist_id) t_swap ON t_swap.dentist_id = dp2.dentist_id
  LEFT JOIN (SELECT s.dentist_id, count(*) cnt FROM "SHIFT" s
              WHERE s.source='SUBSTITUTE' AND s.dentist_id IS NOT NULL
                AND s.shift_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
              GROUP BY s.dentist_id) t_cover ON t_cover.dentist_id = dp2.dentist_id
  WHERE dp2.period_start <= DATE '2026-10-31' AND dp2.period_end >= DATE '2026-10-01'
) q
WHERE dp.performance_id = q.performance_id;

-- 13.3 计算奖励/扣款与最终得分
UPDATE "DENTIST_PERFORMANCE" dp SET
  full_attendance  = (dp.leave_days = 0 AND dp.swap_applied_count = 0),
  attendance_bonus = CASE WHEN dp.leave_days = 0 AND dp.swap_applied_count = 0
                          THEN (SELECT value FROM "PERFORMANCE_RULE" WHERE rule_code='FULL_ATTENDANCE_BONUS')
                          ELSE 0 END,
  leave_penalty    = dp.leave_days * (SELECT value FROM "PERFORMANCE_RULE" WHERE rule_code='LEAVE_DAY_PENALTY'),
  cover_bonus      = dp.cover_shift_count * (SELECT value FROM "PERFORMANCE_RULE" WHERE rule_code='COVER_SHIFT_BONUS')
WHERE dp.period_start <= DATE '2026-10-31' AND dp.period_end >= DATE '2026-10-01';

UPDATE "DENTIST_PERFORMANCE" dp SET
  final_score = COALESCE(dp.performance_score,0) + dp.attendance_bonus + dp.cover_bonus - dp.leave_penalty
WHERE dp.period_start <= DATE '2026-10-31' AND dp.period_end >= DATE '2026-10-01';

-- ---------------------------------------------------------------------
-- 十四、时段余量与营业看板视图
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW "V_SLOT_AVAILABILITY" AS
SELECT b.clinic_id, c.name AS clinic_name, b.slot_date, b.slot_code, t.slot_name,
       t.period_code, t.start_time, t.end_time, b.capacity, b.status AS slot_status,
       COALESCE(u.booked, 0) AS booked,
       b.capacity - COALESCE(u.booked, 0) AS remaining,
       (COALESCE(u.booked,0) >= b.capacity) AS full_flag,
       (SELECT string_agg(st.name, '、' ORDER BY st.staff_id)
          FROM "SHIFT" s JOIN "STAFF" st ON st.staff_id = s.staff_id
         WHERE s.clinic_id=b.clinic_id AND s.shift_date=b.slot_date
           AND s.role_type='DENTIST' AND s.status='PUBLISHED') AS duty_dentists
FROM "BOOKING_SLOT" b
JOIN "CLINIC" c        ON c.clinic_id = b.clinic_id
JOIN "SLOT_TEMPLATE" t ON t.clinic_id = b.clinic_id AND t.slot_code = b.slot_code
LEFT JOIN (SELECT clinic_id, starts_at::date AS d, slot_code, count(*) AS booked
             FROM "APPOINTMENT"
            WHERE status IN ('BOOKED','ARRIVED','COMPLETED') AND slot_code IS NOT NULL
            GROUP BY 1,2,3) u
       ON u.clinic_id = b.clinic_id AND u.d = b.slot_date AND u.slot_code = b.slot_code;

CREATE OR REPLACE VIEW "V_CLINIC_DASHBOARD" AS
WITH days AS (SELECT clinic_id, slot_date FROM "BOOKING_SLOT" GROUP BY clinic_id, slot_date)
SELECT d.clinic_id, c.name AS clinic_name, d.slot_date AS biz_date,
       (SELECT count(*) FROM "APPOINTMENT" a
         WHERE a.clinic_id=d.clinic_id AND a.starts_at::date=d.slot_date
           AND a.status IN ('BOOKED','ARRIVED','COMPLETED')) AS booked_cnt,
       (SELECT count(*) FROM "APPOINTMENT" a
         WHERE a.clinic_id=d.clinic_id AND a.starts_at::date=d.slot_date AND a.status='ARRIVED') AS arrived_cnt,
       (SELECT count(*) FROM "APPOINTMENT" a
         WHERE a.clinic_id=d.clinic_id AND a.starts_at::date=d.slot_date AND a.status='CANCELLED') AS cancelled_cnt,
       (SELECT count(*) FROM "APPOINTMENT" a
         WHERE a.clinic_id=d.clinic_id AND a.starts_at::date=d.slot_date AND a.status='NO_SHOW') AS noshow_cnt,
       (SELECT count(*) FROM "SHIFT" s
         WHERE s.clinic_id=d.clinic_id AND s.shift_date=d.slot_date AND s.role_type='DENTIST' AND s.status='PUBLISHED') AS duty_dentist_cnt,
       (SELECT count(*) FROM "SHIFT" s
         WHERE s.clinic_id=d.clinic_id AND s.shift_date=d.slot_date AND s.role_type='RECEPTION' AND s.status='PUBLISHED') AS duty_reception_cnt,
       COALESCE(rev.revenue,0) AS revenue,
       COALESCE(cst.cost,0)    AS cost,
       COALESCE(rev.revenue,0) - COALESCE(cst.cost,0) AS profit
FROM days d
JOIN "CLINIC" c ON c.clinic_id = d.clinic_id
LEFT JOIN (SELECT a.clinic_id, a.starts_at::date AS d, SUM(p.amount) AS revenue
             FROM "PAYMENT" p
             JOIN "INVOICE_VERSION" iv ON iv.invoice_version_id = p.invoice_version_id
             JOIN "INVOICE" i          ON i.invoice_id = iv.invoice_id
             JOIN "ENCOUNTER" e        ON e.encounter_id = i.encounter_id
             JOIN "APPOINTMENT" a      ON a.appointment_id = e.appointment_id
            WHERE p.status='SUCCESS'
            GROUP BY 1,2) rev ON rev.clinic_id = d.clinic_id AND rev.d = d.slot_date
LEFT JOIN (SELECT a.clinic_id, a.starts_at::date AS d,
                  SUM(tmu.quantity * m.unit_cost) AS cost
             FROM "TREATMENT_MATERIAL_USAGE" tmu
             JOIN "MATERIAL" m ON m.material_id = tmu.material_id
             JOIN "TREATMENT_LINE" tl ON tl.treatment_line_id = tmu.treatment_line_id
             JOIN "ENCOUNTER" e   ON e.encounter_id = tl.encounter_id
             JOIN "APPOINTMENT" a ON a.appointment_id = e.appointment_id
            GROUP BY 1,2) cst ON cst.clinic_id = d.clinic_id AND cst.d = d.slot_date;

-- ---------------------------------------------------------------------
-- 十五、Flyway 记录登记（避免后续版本冲突）
-- ---------------------------------------------------------------------
INSERT INTO flyway_schema_history(installed_rank, version, description, type, script, checksum,
                                  installed_by, installed_on, execution_time, success)
SELECT COALESCE(MAX(installed_rank),0)+1, '3.0.1', 'roster booking performance', 'SQL',
       'V3__adc_roster_booking_performance(part1-5)', 0, 'postgres', now(), 0, true
FROM flyway_schema_history
WHERE NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='3.0.1');

COMMIT;
