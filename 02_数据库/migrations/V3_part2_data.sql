-- =====================================================================
-- ADC 连锁牙科诊所 数据库调整（V3）— 第二部分
-- 数据扩充 / 轮班生成 / 约束与触发器 / 演示预约 / 绩效 / 看板视图
-- 幂等，可重复执行。
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 三、人员数据扩充：每诊所 5 名医生、2 名前台
-- ---------------------------------------------------------------------
-- 3.1 既有员工岗位与归属门店
-- 注：原 staff_id=2/4 曾设为 CLINIC_MANAGER（店长），已按 V4 收敛为 MANAGER（管理层，
--     不按店划分，统一管两家店）。此处直接写 MANAGER 以保证脚本可重放。
UPDATE "STAFF" SET role_type='MANAGER',   home_clinic_id=NULL WHERE staff_id=1;
UPDATE "STAFF" SET role_type='MANAGER',   home_clinic_id=NULL WHERE staff_id=2;
UPDATE "STAFF" SET role_type='RECEPTION', home_clinic_id=1    WHERE staff_id=3;
UPDATE "STAFF" SET role_type='MANAGER',   home_clinic_id=NULL WHERE staff_id=4;
UPDATE "STAFF" SET role_type='RECEPTION', home_clinic_id=2    WHERE staff_id=5;
UPDATE "STAFF" SET role_type='DENTIST',        home_clinic_id=1    WHERE staff_id IN (6,7,8);
UPDATE "STAFF" SET role_type='DENTIST',        home_clinic_id=2    WHERE staff_id IN (9,10);

-- 3.2 新增医生 5 名（天河 2 名、越秀 3 名）与前台 2 名
INSERT INTO "STAFF"(staff_id, staff_no, name, phone, active, role_type, home_clinic_id) VALUES
 (11,'S-D-006','林医生','13900000011',true,'DENTIST',1),
 (12,'S-D-007','徐医生','13900000012',true,'DENTIST',1),
 (13,'S-D-008','黄医生','13900000013',true,'DENTIST',2),
 (14,'S-D-009','邱医生','13900000014',true,'DENTIST',2),
 (15,'S-D-010','冯医生','13900000015',true,'DENTIST',2),
 (16,'S-GZ-A-003','郭敏','13900000016',true,'RECEPTION',1),
 (17,'S-GZ-B-003','何静','13900000017',true,'RECEPTION',2)
ON CONFLICT (staff_id) DO UPDATE SET role_type=EXCLUDED.role_type, home_clinic_id=EXCLUDED.home_clinic_id;

INSERT INTO "DENTIST"(dentist_id, license_no) VALUES
 (11,'DOC-GD-0011'),(12,'DOC-GD-0012'),(13,'DOC-GD-0013'),(14,'DOC-GD-0014'),(15,'DOC-GD-0015')
ON CONFLICT (dentist_id) DO NOTHING;

INSERT INTO "PRACTICE"(dentist_id, clinic_id, valid_from, valid_to, status) VALUES
 (11,1,DATE '2026-01-01',NULL,'ACTIVE'),
 (12,1,DATE '2026-01-01',NULL,'ACTIVE'),
 (13,2,DATE '2026-01-01',NULL,'ACTIVE'),
 (14,2,DATE '2026-01-01',NULL,'ACTIVE'),
 (15,2,DATE '2026-01-01',NULL,'ACTIVE')
ON CONFLICT DO NOTHING;

-- 3.3 新员工登录账号（沿用既有密码，登录密码仍为 AdcDemo@2026!）
WITH ins AS (
  INSERT INTO "ACCOUNT"(login_name, staff_id, active, password_hash)
  SELECT v.login, v.sid, true, (SELECT password_hash FROM "ACCOUNT" WHERE login_name='dentist_zhou')
  FROM (VALUES ('dentist_lin',11),('dentist_xu',12),('dentist_huang',13),('dentist_qiu',14),
               ('dentist_feng',15),('reception_a2',16),('reception_b2',17)) v(login, sid)
  ON CONFLICT (login_name) DO NOTHING
  RETURNING account_id, login_name
)
INSERT INTO "ACCESS_GRANT"(account_id, role_code, scope_type, clinic_id)
SELECT i.account_id, g.role_code, g.scope_type, g.clinic_id
FROM ins i
JOIN (VALUES ('dentist_lin','DENTIST','CLINIC',1),('dentist_xu','DENTIST','CLINIC',1),
             ('dentist_huang','DENTIST','CLINIC',2),('dentist_qiu','DENTIST','CLINIC',2),
             ('dentist_feng','DENTIST','CLINIC',2),('reception_a2','RECEPTION','CLINIC',1),
             ('reception_b2','RECEPTION','CLINIC',2)) g(login, role_code, scope_type, clinic_id)
  ON g.login = i.login_name
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 四、轮班周期与可预约时段
-- ---------------------------------------------------------------------
-- 4.1 轮班周期：医生 5 人循环、每天 3 人；前台 2 人循环、每天 1 人（锚点周一）
INSERT INTO "ROSTER_CYCLE"(clinic_id, role_type, cycle_length, daily_headcount, anchor_date, active) VALUES
 (1,'DENTIST',5,3,DATE '2026-10-26',true),
 (2,'DENTIST',5,3,DATE '2026-10-26',true),
 (1,'RECEPTION',2,1,DATE '2026-10-26',true),
 (2,'RECEPTION',2,1,DATE '2026-10-26',true)
ON CONFLICT (clinic_id, role_type) DO UPDATE
  SET cycle_length=EXCLUDED.cycle_length, daily_headcount=EXCLUDED.daily_headcount, anchor_date=EXCLUDED.anchor_date;

-- 4.2 轮班成员
DELETE FROM "ROSTER_MEMBER";
INSERT INTO "ROSTER_MEMBER"(cycle_id, seq_no, staff_id)
SELECT c.cycle_id, x.seq_no, x.staff_id
FROM "ROSTER_CYCLE" c
JOIN (VALUES (1,'DENTIST',0,6),(1,'DENTIST',1,7),(1,'DENTIST',2,8),(1,'DENTIST',3,11),(1,'DENTIST',4,12),
             (2,'DENTIST',0,9),(2,'DENTIST',1,10),(2,'DENTIST',2,13),(2,'DENTIST',3,14),(2,'DENTIST',4,15),
             (1,'RECEPTION',0,3),(1,'RECEPTION',1,16),
             (2,'RECEPTION',0,5),(2,'RECEPTION',1,17)) x(cid, role_type, seq_no, staff_id)
  ON x.cid = c.clinic_id AND x.role_type = c.role_type;

-- 4.3 可预约时段模板（上午 08-10 / 10-12，下午 14-16 / 16-18，晚上 19-21，每段 2 小时，容量 3）
INSERT INTO "SLOT_TEMPLATE"(clinic_id, slot_code, slot_name, period_code, start_time, end_time, sort_no, capacity, active) VALUES
 (1,'AM1','上午 08:00-10:00','MORNING',  TIME '08:00', TIME '10:00', 1, 3, true),
 (1,'AM2','上午 10:00-12:00','MORNING',  TIME '10:00', TIME '12:00', 2, 3, true),
 (1,'PM1','下午 14:00-16:00','AFTERNOON',TIME '14:00', TIME '16:00', 3, 3, true),
 (1,'PM2','下午 16:00-18:00','AFTERNOON',TIME '16:00', TIME '18:00', 4, 3, true),
 (1,'EV1','晚上 19:00-21:00','EVENING',  TIME '19:00', TIME '21:00', 5, 3, true),
 (2,'AM1','上午 08:00-10:00','MORNING',  TIME '08:00', TIME '10:00', 1, 3, true),
 (2,'AM2','上午 10:00-12:00','MORNING',  TIME '10:00', TIME '12:00', 2, 3, true),
 (2,'PM1','下午 14:00-16:00','AFTERNOON',TIME '14:00', TIME '16:00', 3, 3, true),
 (2,'PM2','下午 16:00-18:00','AFTERNOON',TIME '16:00', TIME '18:00', 4, 3, true),
 (2,'EV1','晚上 19:00-21:00','EVENING',  TIME '19:00', TIME '21:00', 5, 3, true)
ON CONFLICT (clinic_id, slot_code) DO UPDATE
  SET slot_name=EXCLUDED.slot_name, start_time=EXCLUDED.start_time, end_time=EXCLUDED.end_time,
      sort_no=EXCLUDED.sort_no, capacity=EXCLUDED.capacity, active=EXCLUDED.active;

COMMIT;
