-- ADC 牙科诊所数据库自动化测试（PostgreSQL 18）
-- 只读检查 + 回滚事务测试，不改变现有演示数据。
\set ON_ERROR_STOP on
\pset pager off
\echo '=== T01 结构测试 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM information_schema.tables
  WHERE table_schema='public' AND table_type='BASE TABLE';
  IF n <> 33 THEN RAISE EXCEPTION 'T01 failed: expected 33 tables, got %', n; END IF;
END $$;
SELECT 'T01 PASS: 33 tables' AS result;

\echo '=== T02 外键完整性 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM pg_constraint WHERE contype='f' AND connamespace='public'::regnamespace;
  IF n <> 64 THEN RAISE EXCEPTION 'T02 failed: expected 64 foreign keys, got %', n; END IF;
END $$;
SELECT 'T02 PASS: 64 foreign keys' AS result;

\echo '=== T03 核心数据量 ==='
DO $$
BEGIN
  IF (SELECT count(*) FROM "PATIENT") < 10 THEN RAISE EXCEPTION 'T03 failed: patients'; END IF;
  IF (SELECT count(*) FROM "STAFF") < 8 THEN RAISE EXCEPTION 'T03 failed: staff'; END IF;
  IF (SELECT count(*) FROM "ACCOUNT") < 10 THEN RAISE EXCEPTION 'T03 failed: accounts'; END IF;
  IF (SELECT count(*) FROM "APPOINTMENT") < 5 THEN RAISE EXCEPTION 'T03 failed: appointments'; END IF;
END $$;
SELECT 'T03 PASS: demo data coverage' AS result;

\echo '=== T04 预约主链路 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n
  FROM "APPOINTMENT" a JOIN "PATIENT" p ON p."patient_id"=a."patient_id"
  JOIN "SHIFT" sh ON sh."shift_id"=a."shift_id"
  JOIN "DENTIST" d ON d."dentist_id"=sh."dentist_id"
  JOIN "CLINIC" c ON c."clinic_id"=sh."clinic_id";
  IF n <> (SELECT count(*) FROM "APPOINTMENT") THEN RAISE EXCEPTION 'T04 failed: orphan appointment'; END IF;
END $$;
SELECT 'T04 PASS: patient-appointment-shift-dentist-clinic' AS result;

\echo '=== T05 就诊治疗结算主链路 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM "ENCOUNTER" e JOIN "APPOINTMENT" a ON a."appointment_id"=e."appointment_id"
    JOIN "TREATMENT_LINE" tl ON tl."encounter_id"=e."encounter_id"
    JOIN "INVOICE" i ON i."encounter_id"=e."encounter_id"
    JOIN "INVOICE_VERSION" iv ON iv."invoice_id"=i."invoice_id"
    JOIN "INVOICE_LINE" il ON il."invoice_version_id"=iv."invoice_version_id";
  IF n < 2 THEN RAISE EXCEPTION 'T05 failed: expected at least 2 linked treatment invoice rows, got %', n; END IF;
END $$;
SELECT 'T05 PASS: encounter-treatment-invoice chain' AS result;

\echo '=== T06 预约冲突检测 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM "APPOINTMENT"
  WHERE "shift_id"=1 AND "starts_at" < '2026-10-26 09:30:00'
    AND "ends_at" > '2026-10-26 09:00:00'
    AND "status" NOT IN ('CANCELLED','NO_SHOW');
  IF n = 0 THEN RAISE EXCEPTION 'T06 failed: overlap fixture not found'; END IF;
END $$;
SELECT 'T06 PASS: overlap fixture detected; service transaction must reject a new overlapping slot' AS result;

\echo '=== T07 库存非负约束测试（回滚） ==='
BEGIN;
DO $$
BEGIN
  BEGIN
    INSERT INTO "CLINIC_STOCK" ("clinic_id","material_id","on_hand","warning_threshold","lock_version")
    VALUES (1,2,-1,1,1);
    RAISE EXCEPTION 'T07 failed: negative stock accepted';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $$;
ROLLBACK;
SELECT 'T07 PASS: negative stock rejected' AS result;

\echo '=== T08 重复收款请求测试（回滚） ==='
BEGIN;
DO $$
BEGIN
  BEGIN
    INSERT INTO "PAYMENT" ("invoice_version_id","request_no","receipt_no","amount","method","status","paid_at","operator_account_id")
    VALUES (1,'PAY-REQ-20261026-0001','RCPT-DUP-TEST',1,'CASH','SUCCESS',now(),3);
    RAISE EXCEPTION 'T08 failed: duplicate request_no accepted';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;
END $$;
ROLLBACK;
SELECT 'T08 PASS: duplicate payment request rejected' AS result;

\echo '=== T09 绩效公式和等级 ==='
DO $$
BEGIN
  IF (SELECT count(*) FROM "DENTIST_PERFORMANCE" WHERE "performance_score" BETWEEN 0 AND 100) <> (SELECT count(*) FROM "DENTIST_PERFORMANCE") THEN
    RAISE EXCEPTION 'T09 failed: score out of range';
  END IF;
  IF EXISTS (SELECT 1 FROM "DENTIST_PERFORMANCE" WHERE ("performance_score" >= 90 AND "reward_level" <> 'EXCELLENT') OR ("performance_score" >= 75 AND "performance_score" < 90 AND "reward_level" <> 'GOOD') OR ("performance_score" >= 60 AND "performance_score" < 75 AND "reward_level" <> 'NORMAL') OR ("performance_score" < 60 AND "reward_level" <> 'NONE')) THEN
    RAISE EXCEPTION 'T09 failed: reward level mismatch';
  END IF;
END $$;
SELECT 'T09 PASS: score range and reward level' AS result;

\echo '=== T10 外键孤儿检查 ==='
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM "PAYMENT" p LEFT JOIN "INVOICE_VERSION" iv ON iv."invoice_version_id"=p."invoice_version_id" WHERE iv."invoice_version_id" IS NULL;
  IF n <> 0 THEN RAISE EXCEPTION 'T10 failed: orphan payments'; END IF;
  SELECT count(*) INTO n FROM "TREATMENT_LINE" tl LEFT JOIN "ENCOUNTER" e ON e."encounter_id"=tl."encounter_id" WHERE e."encounter_id" IS NULL;
  IF n <> 0 THEN RAISE EXCEPTION 'T10 failed: orphan treatment lines'; END IF;
END $$;
SELECT 'T10 PASS: no orphan payment or treatment rows' AS result;

\echo '=== ALL TESTS PASSED ==='
