-- =====================================================================
-- ADC 连锁牙科诊所 数据库调整（V3）— 第四部分
-- 演示调班/请假（沿用既有三态状态词：SUBMITTED→COORDINATING→IMPLEMENTED）
-- =====================================================================

BEGIN;

-- 9.0 支持前台提交调班/请假，并增加"申请日期/对调目标日期"
ALTER TABLE "SHIFT_REQUEST" ADD COLUMN IF NOT EXISTS applicant_staff_id bigint REFERENCES "STAFF"(staff_id);
ALTER TABLE "SHIFT_REQUEST" ADD COLUMN IF NOT EXISTS requested_date   date;
ALTER TABLE "SHIFT_REQUEST" ADD COLUMN IF NOT EXISTS swap_target_date date;

-- ---------------------------------------------------------------------
-- 十、演示调班/请假（含同店顶班与跨店顶班）
-- ---------------------------------------------------------------------
-- R1 李(column=6 周医生) 2026-10-30 请假，待管理者审核
INSERT INTO "SHIFT_REQUEST"(applicant_dentist_id, applicant_staff_id, request_type, requested_date, swap_target_date,
                            requested_from, requested_to, reason, status, submitted_at)
SELECT 6, 6, 'LEAVE', DATE '2026-10-30', NULL,
       TIMESTAMP '2026-10-30 08:00', TIMESTAMP '2026-10-30 21:00', 'DEMO-R1-请假待审', 'SUBMITTED', now()
WHERE NOT EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R1-请假待审');

-- R2 吴医生 2026-10-29 与林医生 2026-10-31 对调，待对方确认 + 管理者审核
INSERT INTO "SHIFT_REQUEST"(applicant_dentist_id, applicant_staff_id, request_type, requested_date, swap_target_date,
                            requested_from, requested_to, reason, status, submitted_at)
SELECT 7, 7, 'SHIFT_CHANGE', DATE '2026-10-29', DATE '2026-10-31',
       TIMESTAMP '2026-10-29 08:00', TIMESTAMP '2026-10-31 21:00', 'DEMO-R2-对调待确认', 'SUBMITTED', now()
WHERE NOT EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R2-对调待确认');

-- R3 黄医生（越秀）2026-10-28 请假，由同店休假的孙医生顶班（已实施）
INSERT INTO "SHIFT_REQUEST"(applicant_dentist_id, applicant_staff_id, request_type, requested_date, swap_target_date,
                            requested_from, requested_to, reason, status, submitted_at, reviewer_staff_id, reviewed_at)
SELECT 13, 13, 'LEAVE', DATE '2026-10-28', NULL,
       TIMESTAMP '2026-10-28 08:00', TIMESTAMP '2026-10-28 21:00', 'DEMO-R3-同店顶班', 'IMPLEMENTED', now(), 4, now()
WHERE NOT EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R3-同店顶班');

-- R4 徐医生（天河）2026-10-28 请假，同店无人可排，由越秀休假的刘医生跨店顶班（已实施）
INSERT INTO "SHIFT_REQUEST"(applicant_dentist_id, applicant_staff_id, request_type, requested_date, swap_target_date,
                            requested_from, requested_to, reason, status, submitted_at, reviewer_staff_id, reviewed_at)
SELECT 12, 12, 'LEAVE', DATE '2026-10-28', NULL,
       TIMESTAMP '2026-10-28 08:00', TIMESTAMP '2026-10-28 21:00', 'DEMO-R4-跨店顶班', 'IMPLEMENTED', now(), 2, now()
WHERE NOT EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R4-跨店顶班');

-- 10.1 已实施：原排班作废（保留版本链），生成顶班排班
UPDATE "SHIFT" SET status='CANCELLED'
WHERE status='PUBLISHED' AND shift_date = DATE '2026-10-28' AND staff_id = 13
  AND EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R3-同店顶班' AND status='IMPLEMENTED');

UPDATE "SHIFT" SET status='CANCELLED'
WHERE status='PUBLISHED' AND shift_date = DATE '2026-10-28' AND staff_id = 12
  AND EXISTS (SELECT 1 FROM "SHIFT_REQUEST" WHERE reason='DEMO-R4-跨店顶班' AND status='IMPLEMENTED');

INSERT INTO "SHIFT"(shift_series_no, dentist_id, staff_id, clinic_id, role_type, shift_date,
                    starts_at, ends_at, status, version_no, source, substitute_from, origin_request_id,
                    published_by_staff_id, published_at)
SELECT 'SUB-2-20261028-10', 10, 10, 2, 'DENTIST', DATE '2026-10-28',
       TIMESTAMP '2026-10-28 08:00', TIMESTAMP '2026-10-28 21:00', 'PUBLISHED', 1, 'SUBSTITUTE', 'SAME_CLINIC',
       r.request_id, 4, now()
FROM "SHIFT_REQUEST" r WHERE r.reason='DEMO-R3-同店顶班'
  AND NOT EXISTS (SELECT 1 FROM "SHIFT" x WHERE x.shift_series_no='SUB-2-20261028-10');

INSERT INTO "SHIFT"(shift_series_no, dentist_id, staff_id, clinic_id, role_type, shift_date,
                    starts_at, ends_at, status, version_no, source, substitute_from, origin_request_id,
                    published_by_staff_id, published_at)
SELECT 'SUB-1-20261028-9', 9, 9, 1, 'DENTIST', DATE '2026-10-28',
       TIMESTAMP '2026-10-28 08:00', TIMESTAMP '2026-10-28 21:00', 'PUBLISHED', 1, 'SUBSTITUTE', 'CROSS_CLINIC',
       r.request_id, 2, now()
FROM "SHIFT_REQUEST" r WHERE r.reason='DEMO-R4-跨店顶班'
  AND NOT EXISTS (SELECT 1 FROM "SHIFT" x WHERE x.shift_series_no='SUB-1-20261028-9');

-- 10.2 待审请求明细（对调由配合医生确认）
INSERT INTO "SHIFT_REQUEST_ITEM"(request_id, shift_id, proposed_dentist_id, proposed_clinic_id,
                                 proposed_starts_at, proposed_ends_at, counterpart_staff_id,
                                 counterpart_decision, coverage_source)
SELECT r.request_id, s.shift_id, 11, 1, TIMESTAMP '2026-10-29 08:00', TIMESTAMP '2026-10-29 21:00',
       11, 'PENDING', 'SAME_CLINIC'
FROM "SHIFT_REQUEST" r
JOIN "SHIFT" s ON s.staff_id = 7 AND s.shift_date = DATE '2026-10-29' AND s.status='PUBLISHED'
WHERE r.reason='DEMO-R2-对调待确认'
  AND NOT EXISTS (SELECT 1 FROM "SHIFT_REQUEST_ITEM" i WHERE i.request_id = r.request_id);

COMMIT;
