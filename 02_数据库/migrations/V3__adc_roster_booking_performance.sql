-- =====================================================================
-- ADC 连锁牙科诊所 数据库调整（V3）
-- 目标：连锁两店 / 每店 5 医生轮班(每日 3 人在岗) / 每店 2 前台轮班(每日 1 人)
--       / 每诊所每时段预约上限 3 / 调班对调与请假 / 跨店顶班 / 到诊签到
--       / 支付二维码 / 绩效(满勤加、请假扣、顶班加) / 就诊前 12 小时提醒
--       / 管理看板(营业额、利润)
-- 说明：参照开源案例 DentalPin 数据表设计；本脚本幂等，可重复执行。
-- =====================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- 一、既有表字段扩展
-- ---------------------------------------------------------------------
ALTER TABLE "STAFF"      ADD COLUMN IF NOT EXISTS role_type       varchar(32);
ALTER TABLE "STAFF"      ADD COLUMN IF NOT EXISTS home_clinic_id  bigint REFERENCES "CLINIC"(clinic_id);
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS staff_id        bigint REFERENCES "STAFF"(staff_id);
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS role_type       varchar(32);
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS shift_date      date;
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS source          varchar(16) NOT NULL DEFAULT 'ROSTER';
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS substitute_from varchar(16);
ALTER TABLE "SHIFT"      ADD COLUMN IF NOT EXISTS origin_request_id bigint;
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS clinic_id       bigint REFERENCES "CLINIC"(clinic_id);
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS slot_code       varchar(24);
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS booking_no      varchar(32);
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS reminder_sent_at timestamptz;
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS cancelled_at    timestamptz;
ALTER TABLE "APPOINTMENT" ADD COLUMN IF NOT EXISTS rescheduled_from bigint;
ALTER TABLE "PAYMENT"    ADD COLUMN IF NOT EXISTS qr_code          text;
ALTER TABLE "PAYMENT"    ADD COLUMN IF NOT EXISTS qr_expires_at    timestamptz;
ALTER TABLE "PAYMENT"    ADD COLUMN IF NOT EXISTS channel          varchar(16) DEFAULT 'QR';
ALTER TABLE "MATERIAL"   ADD COLUMN IF NOT EXISTS unit_cost        numeric(12,2) NOT NULL DEFAULT 0;

ALTER TABLE "SHIFT_REQUEST_ITEM" ADD COLUMN IF NOT EXISTS counterpart_staff_id  bigint REFERENCES "STAFF"(staff_id);
ALTER TABLE "SHIFT_REQUEST_ITEM" ADD COLUMN IF NOT EXISTS counterpart_decision varchar(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE "SHIFT_REQUEST_ITEM" ADD COLUMN IF NOT EXISTS counterpart_at       timestamptz;
ALTER TABLE "SHIFT_REQUEST_ITEM" ADD COLUMN IF NOT EXISTS coverage_source      varchar(16);

ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS scheduled_days      int     NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS leave_days          numeric NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS swap_applied_count  int     NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS cover_shift_count   int     NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS full_attendance     boolean NOT NULL DEFAULT false;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS attendance_bonus    numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS leave_penalty       numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS cover_bonus         numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE "DENTIST_PERFORMANCE" ADD COLUMN IF NOT EXISTS final_score         numeric(12,2);

-- ---------------------------------------------------------------------
-- 二、新增表
-- ---------------------------------------------------------------------
-- 2.1 轮班周期定义（每诊所每岗位一套）
CREATE TABLE IF NOT EXISTS "ROSTER_CYCLE" (
  cycle_id         bigserial PRIMARY KEY,
  clinic_id        bigint      NOT NULL REFERENCES "CLINIC"(clinic_id),
  role_type        varchar(32) NOT NULL,
  cycle_length     int         NOT NULL,
  daily_headcount  int         NOT NULL,
  anchor_date      date        NOT NULL,
  active           boolean     NOT NULL DEFAULT true,
  UNIQUE (clinic_id, role_type)
);

-- 2.2 轮班成员顺序（seq_no 决定轮换次序）
CREATE TABLE IF NOT EXISTS "ROSTER_MEMBER" (
  cycle_id  bigint REFERENCES "ROSTER_CYCLE"(cycle_id) ON DELETE CASCADE,
  seq_no    int     NOT NULL,
  staff_id  bigint  NOT NULL REFERENCES "STAFF"(staff_id),
  active    boolean NOT NULL DEFAULT true,
  PRIMARY KEY (cycle_id, seq_no)
);

-- 2.3 可预约时段模板（每诊所 5 段，每段 2 小时，默认容量 3）
CREATE TABLE IF NOT EXISTS "SLOT_TEMPLATE" (
  slot_template_id bigserial PRIMARY KEY,
  clinic_id        bigint      NOT NULL REFERENCES "CLINIC"(clinic_id),
  slot_code        varchar(24) NOT NULL,
  slot_name        varchar(32) NOT NULL,
  period_code      varchar(16) NOT NULL,
  start_time       time        NOT NULL,
  end_time         time        NOT NULL,
  sort_no          int         NOT NULL,
  capacity         int         NOT NULL DEFAULT 3,
  active           boolean     NOT NULL DEFAULT true,
  UNIQUE (clinic_id, slot_code)
);

-- 2.4 每日号源（由模板按日期展开，可按日覆盖容量/停诊）
CREATE TABLE IF NOT EXISTS "BOOKING_SLOT" (
  booking_slot_id bigserial PRIMARY KEY,
  clinic_id       bigint      NOT NULL REFERENCES "CLINIC"(clinic_id),
  slot_date       date        NOT NULL,
  slot_code       varchar(24) NOT NULL,
  capacity        int         NOT NULL DEFAULT 3,
  status          varchar(16) NOT NULL DEFAULT 'OPEN',
  remark          varchar(128),
  UNIQUE (clinic_id, slot_date, slot_code)
);

-- 2.5 到诊签到（可由 ENCOUNTER.arrived_at 冗余生成，保留明细便于前台操作留痕）
CREATE TABLE IF NOT EXISTS "CHECKIN" (
  checkin_id      bigserial PRIMARY KEY,
  appointment_id  bigint  NOT NULL REFERENCES "APPOINTMENT"(appointment_id),
  clinic_id       bigint  NOT NULL REFERENCES "CLINIC"(clinic_id),
  patient_id      bigint  NOT NULL REFERENCES "PATIENT"(patient_id),
  arrived_at      timestamptz NOT NULL DEFAULT now(),
  taken_by_account_id bigint REFERENCES "ACCOUNT"(account_id),
  note            varchar(255)
);
CREATE INDEX IF NOT EXISTS ix_checkin_appt ON "CHECKIN"(appointment_id);

-- 2.6 通知/提醒（就诊前 12 小时、审核结果等）
CREATE TABLE IF NOT EXISTS "NOTIFICATION" (
  notification_id bigserial PRIMARY KEY,
  account_id      bigint REFERENCES "ACCOUNT"(account_id),
  patient_id      bigint REFERENCES "PATIENT"(patient_id),
  channel         varchar(16) NOT NULL DEFAULT 'INAPP',
  event_type      varchar(32) NOT NULL,
  title           varchar(128) NOT NULL,
  body            text,
  ref_type        varchar(32),
  ref_id          bigint,
  status          varchar(16) NOT NULL DEFAULT 'PENDING',
  scheduled_at    timestamptz,
  sent_at         timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_notif_sched ON "NOTIFICATION"(status, scheduled_at);

-- 2.7 绩效规则（可按期调整，不写死在代码里）
CREATE TABLE IF NOT EXISTS "PERFORMANCE_RULE" (
  rule_id        bigserial PRIMARY KEY,
  rule_code      varchar(32) NOT NULL UNIQUE,
  rule_name      varchar(64) NOT NULL,
  rule_type      varchar(24) NOT NULL,
  value          numeric(12,2) NOT NULL,
  unit           varchar(16) NOT NULL DEFAULT 'CNY',
  effective_from date NOT NULL DEFAULT DATE '2026-10-01',
  active         boolean NOT NULL DEFAULT true
);

COMMIT;
