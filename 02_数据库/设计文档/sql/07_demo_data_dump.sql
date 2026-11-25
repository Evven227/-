-- 演示数据导出自 adc_dental（2026-10-26）。
-- session_replication_role=replica 用于绕过 PATIENT/SHIFT 的循环外键，仅在初始化时使用。
SET session_replication_role = replica;
--
-- PostgreSQL database dump
--

\restrict 9vBDD5YfjWTKY2hf5pJbvvEJ6sBv6VQx20k7Xh9P8bIen3rAzYCaQzoUpnM5ZLk

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: PATIENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (1, 'P20260001', '林晓梅', '1992-04-12', '广州市天河区体育西路1号', '13800000001', 'lin.xiaomei@example.test', '林建国', '13800000101', NULL, 'RECORDED', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (2, 'P20260002', '陈志远', '1986-11-03', '广州市越秀区东风路2号', '13800000002', 'chen.zhiyuan@example.test', '陈淑兰', '13800000102', NULL, 'NONE_KNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (3, 'P20260003', '周子涵', '2014-07-21', '广州市海珠区新港路3号', '13800000003', 'zhou.zihan@example.test', '周明', '13800000103', '儿童患者', 'RECORDED', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (4, 'P20260004', '黄丽华', '1978-02-18', '广州市白云区机场路4号', '13800000004', 'huang.lihua@example.test', '黄志强', '13800000104', NULL, 'UNKNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (5, 'P20260005', '吴俊杰', '1999-09-09', '广州市番禺区大学城5号', '13800000005', 'wu.junjie@example.test', '吴芳', '13800000105', NULL, 'NONE_KNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (6, 'P20260006', '何静', '1990-12-30', '广州市天河区龙口路6号', '13800000006', 'he.jing@example.test', '何伟', '13800000106', NULL, 'RECORDED', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (7, 'P20260007', '张晨', '2002-05-14', '广州市荔湾区中山路7号', '13800000007', 'zhang.chen@example.test', '张磊', '13800000107', NULL, 'NONE_KNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (8, 'P20260008', '梁思敏', '1984-08-26', '广州市越秀区环市路8号', '13800000008', 'liang.simin@example.test', '梁国华', '13800000108', NULL, 'UNKNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (9, 'P20260009', '罗浩', '1995-03-08', '广州市海珠区江南大道9号', '13800000009', 'luo.hao@example.test', '罗芳', '13800000109', NULL, 'NONE_KNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (10, 'P20260010', '许安琪', '2010-10-19', '广州市白云区云城10号', '13800000010', 'xu.anqi@example.test', '许东', '13800000110', '青少年患者', 'RECORDED', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (11, 'P20260011', '郭建华', '1969-06-05', '广州市天河区员村11号', '13800000011', 'guo.jianhua@example.test', '郭宁', '13800000111', NULL, 'RECORDED', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (12, 'P20260012', '郑婉婷', '1998-01-27', '广州市番禺区洛浦12号', '13800000012', 'zheng.wanting@example.test', '郑伟', '13800000112', NULL, 'NONE_KNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (13, 'PENDING-fe8d621b-8b1a-49b2-a9c1-0e8f4a95afe1', '测试新患者', NULL, NULL, '13800000000', 'test@example.com', NULL, NULL, NULL, 'UNKNOWN', NULL);
INSERT INTO public."PATIENT" (patient_id, master_no, name, birth_date, address, phone, email, emergency_name, emergency_phone, special_needs, allergy_assessment, merged_into_patient_id) VALUES (14, 'PENDING-bbf74e0d-3f1f-4ef2-b2c4-a0282fa6e06a', 'ewen', NULL, NULL, '12345678901', '', NULL, NULL, NULL, 'UNKNOWN', NULL);


--
-- Data for Name: STAFF; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (1, 'S-HQ-001', '赵明远', '13900000001', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (2, 'S-GZ-A-001', '李娜', '13900000002', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (3, 'S-GZ-A-002', '王芳', '13900000003', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (4, 'S-GZ-B-001', '陈洁', '13900000004', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (5, 'S-GZ-B-002', '周敏', '13900000005', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (6, 'S-D-001', '周医生', '13900000006', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (7, 'S-D-002', '吴医生', '13900000007', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (8, 'S-D-003', '郑医生', '13900000008', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (9, 'S-D-004', '刘医生', '13900000009', true);
INSERT INTO public."STAFF" (staff_id, staff_no, name, phone, active) VALUES (10, 'S-D-005', '孙医生', '13900000010', true);


--
-- Data for Name: ACCOUNT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (1, 'hq_manager', NULL, 1, true, '$2a$06$ocF.w4xXSRWw.E/YrBMuvuauVo2VJuJqK3UkhmurPZv192QPrumoK', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (2, 'clinic_manager_a', NULL, 2, true, '$2a$06$s14HO3yXm2QvYopn8KWyV.74abTt5Bd.gADRxpAn6yj3NoJ7v.7GS', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (3, 'reception_a', NULL, 3, true, '$2a$06$.QWLstPHX06/6PwMbVIxGeLMVFlX6ZQnrKHMKaakkbRhOUyRtKNEC', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (4, 'clinic_manager_b', NULL, 4, true, '$2a$06$odUE4cdFLliyHTVydXQP1OuBZ9z5ApojfgINwr9fIjNm6pW28/O3G', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (5, 'reception_b', NULL, 5, true, '$2a$06$35XmkhGbxWH9mgWWVfgUi.n9BJlsBndNL0o8m2ggEScA0l/Y2qRsC', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (6, 'dentist_zhou', NULL, 6, true, '$2a$06$Ae9C5FFMkEBb5NeeTsRcve9dfwHmljK5uzQKyxIsXwJiJhc9ekeuq', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (7, 'dentist_wu', NULL, 7, true, '$2a$06$J4p5hJI9P1KndGCK1ydYFej8l0Vnbsycq5NcpvEdI3kZwK.GS5e7K', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (8, 'dentist_zheng', NULL, 8, true, '$2a$06$BlEG4XDhFe7L5gotcNWbb.45t7GOlXIEhHlGiR/Ks3hqJT1lmbXgq', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (9, 'dentist_liu', NULL, 9, true, '$2a$06$WX4U/NZL7d6/tIkpC.LdFuwXsZoCAe.R3lhwS.L/NrJOpobdau/WO', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (10, 'dentist_sun', NULL, 10, true, '$2a$06$ZPQd0GhGf.0Kc0hb4vAd2.eLP4QUUg0d5ai8GLoko5CWvdBF..g6q', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (11, 'patient_lin', 1, NULL, true, '$2a$06$3ZfBHkW0fga35bcrLUNXber4rhXs7ZaodN/85H1ns4VGALtBSQUNK', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (12, 'patient_chen', 2, NULL, true, '$2a$06$PmITcdQuglJvQ4q1gP59me2d5MoiHnK5LJIe6qyFmlE20KED4AkRa', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (13, 'teacher_test_1026194452', 13, NULL, true, '$2a$06$sDJ5hPlRbjCtb6rYu1r28OsQGCk1HYVeiBCuponEhm7gJ3J3MbhRi', NULL);
INSERT INTO public."ACCOUNT" (account_id, login_name, patient_id, staff_id, active, password_hash, last_login_at) VALUES (14, 'ewen', 14, NULL, true, '$2a$06$FYCC6s8DO5mYjhKb/PZL3ekqcRZYBV9a3hha0XqPPlxmIoxvUaig6', NULL);


--
-- Data for Name: CLINIC; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."CLINIC" (clinic_id, clinic_code, name, address, latitude, longitude, phone, email, manager_staff_id) VALUES (1, 'GZ-TIANHE', 'ADC天河诊所', '广州市天河区体育西路100号', 23.13, 113.32, '020-80000001', 'tianhe@example.test', 2);
INSERT INTO public."CLINIC" (clinic_id, clinic_code, name, address, latitude, longitude, phone, email, manager_staff_id) VALUES (2, 'GZ-YUEXIU', 'ADC越秀诊所', '广州市越秀区东风中路200号', 23.14, 113.27, '020-80000002', 'yuexiu@example.test', 4);


--
-- Data for Name: ACCESS_GRANT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (1, 1, 'HQ_MANAGER', 'CHAIN', NULL);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (2, 2, 'CLINIC_MANAGER', 'CLINIC', 1);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (3, 3, 'RECEPTION', 'CLINIC', 1);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (4, 4, 'CLINIC_MANAGER', 'CLINIC', 2);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (5, 5, 'RECEPTION', 'CLINIC', 2);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (6, 6, 'DENTIST', 'CLINIC', 1);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (7, 7, 'DENTIST', 'CLINIC', 1);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (8, 8, 'DENTIST', 'CHAIN', NULL);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (9, 9, 'DENTIST', 'CLINIC', 2);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (10, 10, 'DENTIST', 'CLINIC', 2);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (11, 11, 'PATIENT', 'SELF', NULL);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (12, 12, 'PATIENT', 'SELF', NULL);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (13, 13, 'PATIENT', 'SELF', NULL);
INSERT INTO public."ACCESS_GRANT" (grant_id, account_id, role_code, scope_type, clinic_id) VALUES (14, 14, 'PATIENT', 'SELF', NULL);


--
-- Data for Name: ALLERGEN; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."ALLERGEN" (allergen_id, name) VALUES (1, '青霉素');
INSERT INTO public."ALLERGEN" (allergen_id, name) VALUES (2, '乳胶');
INSERT INTO public."ALLERGEN" (allergen_id, name) VALUES (3, '布洛芬');


--
-- Data for Name: DENTIST; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."DENTIST" (dentist_id, license_no) VALUES (6, 'DOC-GD-0006');
INSERT INTO public."DENTIST" (dentist_id, license_no) VALUES (7, 'DOC-GD-0007');
INSERT INTO public."DENTIST" (dentist_id, license_no) VALUES (8, 'DOC-GD-0008');
INSERT INTO public."DENTIST" (dentist_id, license_no) VALUES (9, 'DOC-GD-0009');
INSERT INTO public."DENTIST" (dentist_id, license_no) VALUES (10, 'DOC-GD-0010');


--
-- Data for Name: SHIFT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (1, 'SH-A-20261026-01', 6, 1, '2026-10-26 09:00:00', '2026-10-26 12:00:00', 'PUBLISHED', 1, NULL, 2, '2026-10-20 09:00:00+08');
INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (2, 'SH-A-20261026-02', 7, 1, '2026-10-26 13:30:00', '2026-10-26 17:30:00', 'PUBLISHED', 1, NULL, 2, '2026-10-20 09:00:00+08');
INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (3, 'SH-A-20261027-01', 8, 1, '2026-10-27 09:00:00', '2026-10-27 12:00:00', 'PUBLISHED', 1, NULL, 2, '2026-10-20 09:00:00+08');
INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (4, 'SH-B-20261026-01', 9, 2, '2026-10-26 09:00:00', '2026-10-26 12:00:00', 'PUBLISHED', 1, NULL, 4, '2026-10-20 09:00:00+08');
INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (5, 'SH-B-20261027-01', 10, 2, '2026-10-27 13:30:00', '2026-10-27 17:30:00', 'PUBLISHED', 1, NULL, 4, '2026-10-20 09:00:00+08');
INSERT INTO public."SHIFT" (shift_id, shift_series_no, dentist_id, clinic_id, starts_at, ends_at, status, version_no, previous_shift_id, published_by_staff_id, published_at) VALUES (6, 'SH-A-20261028-01', 6, 1, '2026-10-28 09:00:00', '2026-10-28 12:00:00', 'PUBLISHED', 1, NULL, 2, '2026-10-20 09:00:00+08');


--
-- Data for Name: APPOINTMENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (1, 1, 1, '2026-10-26 09:00:00', '2026-10-26 09:30:00', 'COMPLETED', 3, '2026-10-20 10:00:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (2, 2, 1, '2026-10-26 09:30:00', '2026-10-26 10:00:00', 'ARRIVED', 11, '2026-10-20 10:05:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (4, 4, 3, '2026-10-27 09:00:00', '2026-10-27 09:30:00', 'CANCELLED', 3, '2026-10-20 10:15:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (5, 5, 4, '2026-10-26 09:00:00', '2026-10-26 09:30:00', 'NO_SHOW', 5, '2026-10-20 10:20:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (6, 6, 5, '2026-10-27 13:30:00', '2026-10-27 14:00:00', 'CONFIRMED', 5, '2026-10-20 10:25:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (7, 7, 6, '2026-10-28 09:00:00', '2026-10-28 09:30:00', 'BOOKED', 12, '2026-10-20 10:30:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (8, 8, 2, '2026-10-26 14:00:00', '2026-10-26 14:30:00', 'BOOKED', 3, '2026-10-20 10:35:00+08');
INSERT INTO public."APPOINTMENT" (appointment_id, patient_id, shift_id, starts_at, ends_at, status, created_by_account_id, created_at) VALUES (3, 3, 2, '2026-10-26 15:00:00', '2026-10-26 15:30:00', 'BOOKED', 3, '2026-10-20 10:10:00+08');


--
-- Data for Name: APPOINTMENT_EVENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."APPOINTMENT_EVENT" (event_id, appointment_id, old_status, new_status, old_shift_id, new_shift_id, old_starts_at, old_ends_at, new_starts_at, new_ends_at, reason, operator_account_id, occurred_at) VALUES (1, 1, 'BOOKED', 'CONFIRMED', 1, 1, '2026-10-26 09:00:00', '2026-10-26 09:30:00', '2026-10-26 09:00:00', '2026-10-26 09:30:00', '患者确认', 3, '2026-10-21 09:00:00+08');
INSERT INTO public."APPOINTMENT_EVENT" (event_id, appointment_id, old_status, new_status, old_shift_id, new_shift_id, old_starts_at, old_ends_at, new_starts_at, new_ends_at, reason, operator_account_id, occurred_at) VALUES (2, 4, 'BOOKED', 'CANCELLED', 3, 3, '2026-10-27 09:00:00', '2026-10-27 09:30:00', '2026-10-27 09:00:00', '2026-10-27 09:30:00', '患者临时取消', 3, '2026-10-22 09:00:00+08');
INSERT INTO public."APPOINTMENT_EVENT" (event_id, appointment_id, old_status, new_status, old_shift_id, new_shift_id, old_starts_at, old_ends_at, new_starts_at, new_ends_at, reason, operator_account_id, occurred_at) VALUES (3, 5, 'BOOKED', 'NO_SHOW', 4, 4, '2026-10-26 09:00:00', '2026-10-26 09:30:00', '2026-10-26 09:00:00', '2026-10-26 09:30:00', '超过签到时间', 5, '2026-10-26 09:45:00+08');
INSERT INTO public."APPOINTMENT_EVENT" (event_id, appointment_id, old_status, new_status, old_shift_id, new_shift_id, old_starts_at, old_ends_at, new_starts_at, new_ends_at, reason, operator_account_id, occurred_at) VALUES (4, 3, 'BOOKED', 'BOOKED', 2, 2, '2026-10-26 13:30:00', '2026-10-26 14:00:00', '2026-10-26 15:00:00', '2026-10-26 15:30:00', 'RESCHEDULED', 1, '2026-10-26 19:52:40.296684+08');


--
-- Data for Name: TREATMENT_ITEM; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."TREATMENT_ITEM" (treatment_item_id, item_code, name, standard_unit, active) VALUES (1, 'EXAM', '口腔检查', '次', true);
INSERT INTO public."TREATMENT_ITEM" (treatment_item_id, item_code, name, standard_unit, active) VALUES (2, 'CLEAN', '洁牙', '次', true);
INSERT INTO public."TREATMENT_ITEM" (treatment_item_id, item_code, name, standard_unit, active) VALUES (3, 'FILL', '树脂补牙', '颗', true);
INSERT INTO public."TREATMENT_ITEM" (treatment_item_id, item_code, name, standard_unit, active) VALUES (4, 'ORTHO-CONSULT', '正畸咨询', '次', true);
INSERT INTO public."TREATMENT_ITEM" (treatment_item_id, item_code, name, standard_unit, active) VALUES (5, 'PEDO-FLUORIDE', '儿童涂氟', '次', true);


--
-- Data for Name: APPOINTMENT_REQUEST_ITEM; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (1, 2);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (2, 1);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (3, 5);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (4, 3);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (5, 2);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (6, 4);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (7, 1);
INSERT INTO public."APPOINTMENT_REQUEST_ITEM" (appointment_id, treatment_item_id) VALUES (8, 2);


--
-- Data for Name: AUDIT_EVENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."AUDIT_EVENT" (audit_id, actor_account_id, object_type, object_key, action, reason, before_value, after_value, occurred_at) VALUES (1, 3, 'APPOINTMENT', '1', 'CONFIRM', '患者确认', '{"status": "BOOKED"}', '{"status": "CONFIRMED"}', '2026-10-21 09:00:00+08');
INSERT INTO public."AUDIT_EVENT" (audit_id, actor_account_id, object_type, object_key, action, reason, before_value, after_value, occurred_at) VALUES (2, 5, 'PAYMENT', '2', 'CREATE', '完成收款', NULL, '{"amount": 120.00, "status": "SUCCESS"}', '2026-10-27 14:10:00+08');


--
-- Data for Name: MATERIAL; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."MATERIAL" (material_id, material_code, name, unit, active) VALUES (1, 'MAT-GLOVE', '一次性乳胶手套', '盒', true);
INSERT INTO public."MATERIAL" (material_id, material_code, name, unit, active) VALUES (2, 'MAT-MASK', '医用口罩', '盒', true);
INSERT INTO public."MATERIAL" (material_id, material_code, name, unit, active) VALUES (3, 'MAT-FLOSS', '牙线', '包', true);


--
-- Data for Name: CLINIC_STOCK; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."CLINIC_STOCK" (clinic_id, material_id, on_hand, warning_threshold, lock_version) VALUES (1, 1, 18.000, 5.000, 1);
INSERT INTO public."CLINIC_STOCK" (clinic_id, material_id, on_hand, warning_threshold, lock_version) VALUES (1, 2, 30.000, 10.000, 1);
INSERT INTO public."CLINIC_STOCK" (clinic_id, material_id, on_hand, warning_threshold, lock_version) VALUES (1, 3, 45.000, 10.000, 1);
INSERT INTO public."CLINIC_STOCK" (clinic_id, material_id, on_hand, warning_threshold, lock_version) VALUES (2, 1, 12.000, 5.000, 1);
INSERT INTO public."CLINIC_STOCK" (clinic_id, material_id, on_hand, warning_threshold, lock_version) VALUES (2, 2, 20.000, 10.000, 1);


--
-- Data for Name: DENTIST_PERFORMANCE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."DENTIST_PERFORMANCE" (performance_id, dentist_id, clinic_id, period_start, period_end, appointment_count, attended_count, cancelled_count, no_show_count, completed_count, treatment_count, workload_target_count, performance_score, reward_level, review_status, reviewed_by_account_id, reviewed_at, calculation_version, calculated_at, created_at) VALUES (1, 6, 1, '2026-10-01', '2026-11-01', 2, 2, 0, 0, 1, 3, 3, 80.00, 'GOOD', 'CONFIRMED', 2, '2026-10-29 10:00:00+08', 'v1', '2026-10-29 09:00:00+08', '2026-10-29 09:00:00+08');
INSERT INTO public."DENTIST_PERFORMANCE" (performance_id, dentist_id, clinic_id, period_start, period_end, appointment_count, attended_count, cancelled_count, no_show_count, completed_count, treatment_count, workload_target_count, performance_score, reward_level, review_status, reviewed_by_account_id, reviewed_at, calculation_version, calculated_at, created_at) VALUES (2, 10, 2, '2026-10-01', '2026-11-01', 1, 1, 0, 0, 1, 1, 2, 70.00, 'NORMAL', 'DRAFT', NULL, NULL, 'v1', '2026-10-29 09:05:00+08', '2026-10-29 09:05:00+08');


--
-- Data for Name: QUALIFICATION; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."QUALIFICATION" (qualification_id, code, name) VALUES (1, 'GENERAL', '口腔执业医师');
INSERT INTO public."QUALIFICATION" (qualification_id, code, name) VALUES (2, 'ORTHO', '口腔正畸资质');
INSERT INTO public."QUALIFICATION" (qualification_id, code, name) VALUES (3, 'PEDO', '儿童口腔资质');


--
-- Data for Name: DENTIST_QUALIFICATION; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (1, 6, 1, 'CERT-0006-G', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (2, 7, 1, 'CERT-0007-G', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (3, 8, 1, 'CERT-0008-G', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (4, 8, 2, 'CERT-0008-O', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (5, 9, 1, 'CERT-0009-G', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (6, 10, 1, 'CERT-0010-G', '2025-01-01', '2030-12-31');
INSERT INTO public."DENTIST_QUALIFICATION" (dentist_qualification_id, dentist_id, qualification_id, certificate_no, valid_from, valid_to) VALUES (7, 10, 3, 'CERT-0010-P', '2025-01-01', '2030-12-31');


--
-- Data for Name: ENCOUNTER; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."ENCOUNTER" (encounter_id, appointment_id, chief_complaint, diagnosis, special_needs, status, arrived_at, arrived_by_account_id, completed_at, confirmed_by_dentist_id) VALUES (1, 1, '牙齿清洁', '牙菌斑较多', NULL, 'COMPLETED', '2026-10-26 08:55:00+08', 3, '2026-10-26 09:25:00+08', 6);
INSERT INTO public."ENCOUNTER" (encounter_id, appointment_id, chief_complaint, diagnosis, special_needs, status, arrived_at, arrived_by_account_id, completed_at, confirmed_by_dentist_id) VALUES (2, 2, '牙痛', '待进一步检查', NULL, 'IN_PROGRESS', '2026-10-26 09:25:00+08', 3, NULL, 6);
INSERT INTO public."ENCOUNTER" (encounter_id, appointment_id, chief_complaint, diagnosis, special_needs, status, arrived_at, arrived_by_account_id, completed_at, confirmed_by_dentist_id) VALUES (3, 6, '正畸咨询', '牙列不齐', NULL, 'COMPLETED', '2026-10-27 13:20:00+08', 5, '2026-10-27 14:00:00+08', 10);


--
-- Data for Name: INVOICE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."INVOICE" (invoice_id, encounter_id, invoice_no, status) VALUES (1, 1, 'INV-20261026-0001', 'PAID');
INSERT INTO public."INVOICE" (invoice_id, encounter_id, invoice_no, status) VALUES (2, 2, 'INV-20261026-0002', 'DRAFT');
INSERT INTO public."INVOICE" (invoice_id, encounter_id, invoice_no, status) VALUES (3, 3, 'INV-20261027-0001', 'CONFIRMED');


--
-- Data for Name: INVOICE_VERSION; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."INVOICE_VERSION" (invoice_version_id, invoice_id, version_no, total_amount, currency, issued_at, issued_by_account_id, correction_reason) VALUES (1, 1, 1, 340.00, 'CNY', '2026-10-26 09:30:00+08', 3, NULL);
INSERT INTO public."INVOICE_VERSION" (invoice_version_id, invoice_id, version_no, total_amount, currency, issued_at, issued_by_account_id, correction_reason) VALUES (2, 2, 1, 80.00, 'CNY', '2026-10-26 10:00:00+08', 3, NULL);
INSERT INTO public."INVOICE_VERSION" (invoice_version_id, invoice_id, version_no, total_amount, currency, issued_at, issued_by_account_id, correction_reason) VALUES (3, 3, 1, 120.00, 'CNY', '2026-10-27 14:05:00+08', 5, NULL);


--
-- Data for Name: PRICE_VERSION; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."PRICE_VERSION" (price_version_id, treatment_item_id, version_no, unit, unit_price, currency, valid_from, valid_to) VALUES (1, 1, 1, '次', 80.00, 'CNY', '2026-01-01', NULL);
INSERT INTO public."PRICE_VERSION" (price_version_id, treatment_item_id, version_no, unit, unit_price, currency, valid_from, valid_to) VALUES (2, 2, 1, '次', 260.00, 'CNY', '2026-01-01', NULL);
INSERT INTO public."PRICE_VERSION" (price_version_id, treatment_item_id, version_no, unit, unit_price, currency, valid_from, valid_to) VALUES (3, 3, 1, '颗', 380.00, 'CNY', '2026-01-01', NULL);
INSERT INTO public."PRICE_VERSION" (price_version_id, treatment_item_id, version_no, unit, unit_price, currency, valid_from, valid_to) VALUES (4, 4, 1, '次', 120.00, 'CNY', '2026-01-01', NULL);
INSERT INTO public."PRICE_VERSION" (price_version_id, treatment_item_id, version_no, unit, unit_price, currency, valid_from, valid_to) VALUES (5, 5, 1, '次', 150.00, 'CNY', '2026-01-01', NULL);


--
-- Data for Name: TREATMENT_LINE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."TREATMENT_LINE" (treatment_line_id, encounter_id, treatment_item_id, price_version_id, quantity, name_snapshot, unit_snapshot, unit_price_snapshot, notes, confirmed_at) VALUES (1, 1, 1, 1, 1.000, '口腔检查', '次', 80.00, '常规检查', '2026-10-26 09:10:00+08');
INSERT INTO public."TREATMENT_LINE" (treatment_line_id, encounter_id, treatment_item_id, price_version_id, quantity, name_snapshot, unit_snapshot, unit_price_snapshot, notes, confirmed_at) VALUES (2, 1, 2, 2, 1.000, '洁牙', '次', 260.00, '超声洁牙', '2026-10-26 09:20:00+08');
INSERT INTO public."TREATMENT_LINE" (treatment_line_id, encounter_id, treatment_item_id, price_version_id, quantity, name_snapshot, unit_snapshot, unit_price_snapshot, notes, confirmed_at) VALUES (3, 2, 1, 1, 1.000, '口腔检查', '次', 80.00, '检查疼痛部位', NULL);
INSERT INTO public."TREATMENT_LINE" (treatment_line_id, encounter_id, treatment_item_id, price_version_id, quantity, name_snapshot, unit_snapshot, unit_price_snapshot, notes, confirmed_at) VALUES (4, 3, 4, 4, 1.000, '正畸咨询', '次', 120.00, '初诊咨询', '2026-10-27 13:50:00+08');


--
-- Data for Name: INVOICE_LINE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."INVOICE_LINE" (invoice_line_id, invoice_version_id, treatment_line_id, description_snapshot, unit_snapshot, quantity_snapshot, unit_price_snapshot, line_total) VALUES (1, 1, 1, '口腔检查', '次', 1.000, 80.00, 80.00);
INSERT INTO public."INVOICE_LINE" (invoice_line_id, invoice_version_id, treatment_line_id, description_snapshot, unit_snapshot, quantity_snapshot, unit_price_snapshot, line_total) VALUES (2, 1, 2, '洁牙', '次', 1.000, 260.00, 260.00);
INSERT INTO public."INVOICE_LINE" (invoice_line_id, invoice_version_id, treatment_line_id, description_snapshot, unit_snapshot, quantity_snapshot, unit_price_snapshot, line_total) VALUES (3, 2, 3, '口腔检查', '次', 1.000, 80.00, 80.00);
INSERT INTO public."INVOICE_LINE" (invoice_line_id, invoice_version_id, treatment_line_id, description_snapshot, unit_snapshot, quantity_snapshot, unit_price_snapshot, line_total) VALUES (4, 3, 4, '正畸咨询', '次', 1.000, 120.00, 120.00);


--
-- Data for Name: PATIENT_ALLERGY; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."PATIENT_ALLERGY" (patient_id, allergen_id, reaction, notes, recorded_at) VALUES (1, 1, '皮疹', '患者自述既往用药后出现皮疹', '2026-10-01 09:00:00+08');
INSERT INTO public."PATIENT_ALLERGY" (patient_id, allergen_id, reaction, notes, recorded_at) VALUES (3, 2, '接触性红斑', '儿童患者使用乳胶手套需注意', '2026-10-02 10:00:00+08');
INSERT INTO public."PATIENT_ALLERGY" (patient_id, allergen_id, reaction, notes, recorded_at) VALUES (6, 3, '胃部不适', '避免自行服用布洛芬', '2026-10-03 11:00:00+08');
INSERT INTO public."PATIENT_ALLERGY" (patient_id, allergen_id, reaction, notes, recorded_at) VALUES (10, 1, '皮疹', '监护人确认', '2026-10-04 14:00:00+08');


--
-- Data for Name: PAYMENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."PAYMENT" (payment_id, invoice_version_id, request_no, receipt_no, amount, method, status, paid_at, operator_account_id) VALUES (1, 1, 'PAY-REQ-20261026-0001', 'RCPT-20261026-0001', 340.00, 'MOBILE_PAYMENT', 'SUCCESS', '2026-10-26 09:35:00+08', 3);
INSERT INTO public."PAYMENT" (payment_id, invoice_version_id, request_no, receipt_no, amount, method, status, paid_at, operator_account_id) VALUES (2, 3, 'PAY-REQ-20261027-0001', 'RCPT-20261027-0001', 120.00, 'BANK_CARD', 'SUCCESS', '2026-10-27 14:10:00+08', 5);


--
-- Data for Name: PRACTICE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (1, 6, 1, '2025-01-01', NULL, 'ACTIVE');
INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (2, 7, 1, '2025-01-01', NULL, 'ACTIVE');
INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (3, 8, 1, '2025-01-01', NULL, 'ACTIVE');
INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (4, 8, 2, '2025-01-01', NULL, 'ACTIVE');
INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (5, 9, 2, '2025-01-01', NULL, 'ACTIVE');
INSERT INTO public."PRACTICE" (practice_id, dentist_id, clinic_id, valid_from, valid_to, status) VALUES (6, 10, 2, '2025-01-01', NULL, 'ACTIVE');


--
-- Data for Name: SHIFT_REQUEST; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."SHIFT_REQUEST" (request_id, applicant_dentist_id, request_type, requested_from, requested_to, reason, status, submitted_at, reviewer_staff_id, reviewed_at, rejection_reason) VALUES (1, 7, 'LEAVE', '2026-10-28 13:30:00', '2026-10-28 17:30:00', '参加继续教育培训', 'COORDINATING', '2026-10-21 15:00:00+08', 2, '2026-10-22 09:00:00+08', NULL);
INSERT INTO public."SHIFT_REQUEST" (request_id, applicant_dentist_id, request_type, requested_from, requested_to, reason, status, submitted_at, reviewer_staff_id, reviewed_at, rejection_reason) VALUES (2, 6, 'SHIFT_CHANGE', '2026-10-28 09:00:00', '2026-10-28 13:00:00', '参加课程活动申请调班', 'SUBMITTED', '2026-10-26 19:45:22.972573+08', NULL, NULL, NULL);


--
-- Data for Name: REQUEST_APPOINTMENT_IMPACT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."REQUEST_APPOINTMENT_IMPACT" (request_id, appointment_id, resolution_status, resolution_event_id, resolved_by_account_id, resolved_at) VALUES (1, 3, 'PENDING', NULL, NULL, NULL);


--
-- Data for Name: SHIFT_REQUEST_ITEM; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."SHIFT_REQUEST_ITEM" (request_id, shift_id, proposed_dentist_id, proposed_clinic_id, proposed_starts_at, proposed_ends_at, result_shift_id) VALUES (1, 2, 8, 1, '2026-10-26 13:30:00', '2026-10-26 17:30:00', 3);


--
-- Data for Name: TREATMENT_MATERIAL_USAGE; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."TREATMENT_MATERIAL_USAGE" (usage_id, treatment_line_id, clinic_id, material_id, request_no, quantity, recorded_at) VALUES (1, 1, 1, 1, 'USE-20261026-0001', 1.000, '2026-10-26 09:22:00+08');
INSERT INTO public."TREATMENT_MATERIAL_USAGE" (usage_id, treatment_line_id, clinic_id, material_id, request_no, quantity, recorded_at) VALUES (2, 2, 1, 3, 'USE-20261026-0002', 1.000, '2026-10-26 09:23:00+08');


--
-- Data for Name: STOCK_MOVEMENT; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."STOCK_MOVEMENT" (movement_id, clinic_id, material_id, usage_id, movement_type, quantity_delta, reason, occurred_at, operator_account_id) VALUES (1, 1, 1, NULL, 'INBOUND', 20.000, '月度入库', '2026-10-01 09:00:00+08', 2);
INSERT INTO public."STOCK_MOVEMENT" (movement_id, clinic_id, material_id, usage_id, movement_type, quantity_delta, reason, occurred_at, operator_account_id) VALUES (2, 1, 1, 1, 'USAGE', -1.000, '洁牙耗用', '2026-10-26 09:22:00+08', 3);
INSERT INTO public."STOCK_MOVEMENT" (movement_id, clinic_id, material_id, usage_id, movement_type, quantity_delta, reason, occurred_at, operator_account_id) VALUES (3, 1, 3, 2, 'USAGE', -1.000, '洁牙耗用', '2026-10-26 09:23:00+08', 3);


--
-- Data for Name: TREATMENT_REQUIRED_QUALIFICATION; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public."TREATMENT_REQUIRED_QUALIFICATION" (treatment_item_id, qualification_id) VALUES (1, 1);
INSERT INTO public."TREATMENT_REQUIRED_QUALIFICATION" (treatment_item_id, qualification_id) VALUES (2, 1);
INSERT INTO public."TREATMENT_REQUIRED_QUALIFICATION" (treatment_item_id, qualification_id) VALUES (3, 1);
INSERT INTO public."TREATMENT_REQUIRED_QUALIFICATION" (treatment_item_id, qualification_id) VALUES (4, 2);
INSERT INTO public."TREATMENT_REQUIRED_QUALIFICATION" (treatment_item_id, qualification_id) VALUES (5, 3);


--
-- Name: ACCESS_GRANT_grant_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."ACCESS_GRANT_grant_id_seq"', 14, true);


--
-- Name: ACCOUNT_account_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."ACCOUNT_account_id_seq"', 14, true);


--
-- Name: ALLERGEN_allergen_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."ALLERGEN_allergen_id_seq"', 3, true);


--
-- Name: APPOINTMENT_EVENT_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."APPOINTMENT_EVENT_event_id_seq"', 4, true);


--
-- Name: APPOINTMENT_appointment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."APPOINTMENT_appointment_id_seq"', 8, true);


--
-- Name: AUDIT_EVENT_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."AUDIT_EVENT_audit_id_seq"', 2, true);


--
-- Name: CLINIC_clinic_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."CLINIC_clinic_id_seq"', 2, true);


--
-- Name: DENTIST_PERFORMANCE_performance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."DENTIST_PERFORMANCE_performance_id_seq"', 2, true);


--
-- Name: DENTIST_QUALIFICATION_dentist_qualification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."DENTIST_QUALIFICATION_dentist_qualification_id_seq"', 7, true);


--
-- Name: DENTIST_dentist_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."DENTIST_dentist_id_seq"', 10, true);


--
-- Name: ENCOUNTER_encounter_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."ENCOUNTER_encounter_id_seq"', 3, true);


--
-- Name: INVOICE_LINE_invoice_line_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."INVOICE_LINE_invoice_line_id_seq"', 4, true);


--
-- Name: INVOICE_VERSION_invoice_version_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."INVOICE_VERSION_invoice_version_id_seq"', 3, true);


--
-- Name: INVOICE_invoice_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."INVOICE_invoice_id_seq"', 3, true);


--
-- Name: MATERIAL_material_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."MATERIAL_material_id_seq"', 3, true);


--
-- Name: PATIENT_patient_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."PATIENT_patient_id_seq"', 14, true);


--
-- Name: PAYMENT_payment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."PAYMENT_payment_id_seq"', 3, true);


--
-- Name: PRACTICE_practice_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."PRACTICE_practice_id_seq"', 6, true);


--
-- Name: PRICE_VERSION_price_version_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."PRICE_VERSION_price_version_id_seq"', 5, true);


--
-- Name: QUALIFICATION_qualification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."QUALIFICATION_qualification_id_seq"', 3, true);


--
-- Name: SHIFT_REQUEST_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."SHIFT_REQUEST_request_id_seq"', 2, true);


--
-- Name: SHIFT_shift_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."SHIFT_shift_id_seq"', 6, true);


--
-- Name: STAFF_staff_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."STAFF_staff_id_seq"', 10, true);


--
-- Name: STOCK_MOVEMENT_movement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."STOCK_MOVEMENT_movement_id_seq"', 3, true);


--
-- Name: TREATMENT_ITEM_treatment_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."TREATMENT_ITEM_treatment_item_id_seq"', 5, true);


--
-- Name: TREATMENT_LINE_treatment_line_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."TREATMENT_LINE_treatment_line_id_seq"', 4, true);


--
-- Name: TREATMENT_MATERIAL_USAGE_usage_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public."TREATMENT_MATERIAL_USAGE_usage_id_seq"', 2, true);


--
-- PostgreSQL database dump complete
--

\unrestrict 9vBDD5YfjWTKY2hf5pJbvvEJ6sBv6VQx20k7Xh9P8bIen3rAzYCaQzoUpnM5ZLk

