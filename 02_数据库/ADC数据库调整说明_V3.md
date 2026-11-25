# ADC 连锁牙科诊所 — 数据库调整说明（V3）

> 库：`adc_dental`（PostgreSQL 18.6 @ 127.0.0.1:5433）
> 依据需求：连锁两店 / 5 医生轮班 / 前台轮班 / 时段容量 / 调班与请假 / 跨店顶班 / 到诊 / 二维码支付 / 绩效 / 12 小时提醒 / 管理看板
> 参照开源案例 DentalPin 的数据建模（周排班、号源、状态事件、支付请求）

---

## 0. 角色体系（V4 已收敛）

全系统只有四类角色，**不区分单店店长与总部管理员，统一为「管理层」**：

| 角色码 | 中文名 | 数据范围 | 账号 |
|---|---|---|---|
| PATIENT | 患者 | SELF（仅本人） | patient_lin、patient_chen、teacher_test_*、ewen |
| RECEPTION | 前台 | CLINIC（本店） | reception_a、reception_a2（天河）；reception_b、reception_b2（越秀） |
| DENTIST | 医生 | CLINIC（本店） | dentist_zhou/wu/zheng/lin/xu（天河）；dentist_liu/sun/huang/qiu/feng（越秀） |
| **MANAGER** | **管理层** | **CHAIN（两店全量）** | **hq_manager（赵明远）、manager_a（李娜）、manager_b（陈洁）** |

要点：
- 原 `CLINIC_MANAGER`（店长）与 `HQ_MANAGER`（总管理员）**已合并为 MANAGER**，三个管理层账号权限完全一致，都能看两家店的预约、到诊、排班、营业额与利润。
- 管理层 `home_clinic_id` 统一置空（归属连锁总部，不绑定单店）。
- 字典已用 CHECK 约束锁死：`ACCESS_GRANT.role_code ∈ {PATIENT, RECEPTION, DENTIST, MANAGER}`、`STAFF.role_type ∈ {DENTIST, RECEPTION, MANAGER}`，防止再出现店长类角色。
- 调班/请假审核统一由管理层处理（无"先店长后总部"两级流程）。

> 密码统一 `AdcDemo@2026!`。

### 0.1 口径约定（V5 已确认并固化）

| 口径 | 约定 | 实现位置 | 校验结果 |
|---|---|---|---|
| 医生可见范围 | **全部一致**：医生只能看本诊所数据 | `ACCESS_GRANT` 一律 `DENTIST / CLINIC / 归属店` | 天河 5 人 + 越秀 5 人，无一例外 |
| 号源容量 | **每诊所每时段 3 个名额**（不是每位医生 3 个） | `BOOKING_SLOT` 唯一键 `(clinic_id, slot_date, slot_code)`，表内**无任何医生/员工维度列**；`capacity=3` | 10 个（诊所×时段）组合容量全为 3 |
| 利润 | **营业额 − 耗材成本** | `V_CLINIC_DASHBOARD`：`revenue=∑PAYMENT.amount(status=SUCCESS)`，`cost=∑(TREATMENT_MATERIAL_USAGE.quantity × MATERIAL.unit_cost)`，`profit=revenue−cost` | 天河 10/28：2020 − 57 = 1963 ✔ |

> 容量含义：同一诊所同一 2 小时时段，**累计 3 名患者约满**，与当天值班几位医生无关；
> 换挂其他医生的排班也不能突破该上限（已实测拦截）。

> **注意（代码层需处理）**：跨店顶班日，顶班医生的可见范围仍为其归属诊所（CLINIC）。
> 若需让顶班医生在对方的当日看到对方诊所排班/预约，应在代码层按 `SHIFT.source='SUBSTITUTE'`
> 临时放开当日数据范围，而不是把医生的 `scope_type` 改成 CHAIN。

---

## 1. 既有表改造

| 表 | 调整 | 作用 |
|---|---|---|
| STAFF | +role_type、+home_clinic_id | 区分医生/前台/管理者及其归属门店 |
| SHIFT | +staff_id、+role_type、+shift_date、+source、+substitute_from、+origin_request_id；dentist_id 改为可空 | 排班同时覆盖医生与前台；source 区分 ROSTER/SUBSTITUTE 以支持顶班 |
| APPOINTMENT | +clinic_id、+slot_code、+booking_no、+reminder_sent_at、+cancelled_at、+rescheduled_from | 预约绑定到"诊所+日期+时段"，支持改约留痕 |
| PAYMENT | +qr_code、+qr_expires_at、+channel | 前台生成收款二维码（既有 request_no 作为二维码载荷） |
| MATERIAL | +unit_cost | 看板计算成本与利润 |
| SHIFT_REQUEST | +applicant_staff_id、+requested_date、+swap_target_date | 前台也能请假/调班；对调记录"从哪天调到哪天" |
| SHIFT_REQUEST_ITEM | +counterpart_staff_id、+counterpart_decision、+coverage_source | 对调需配合方确认；记录同店/跨店顶班来源 |
| DENTIST_PERFORMANCE | +scheduled_days、leave_days、swap_applied_count、cover_shift_count、full_attendance、attendance_bonus、leave_penalty、cover_bonus、final_score | 满勤加成、请假扣款、顶班补贴 |

## 2. 新增表

| 表 | 用途 | 关键字段 |
|---|---|---|
| ROSTER_CYCLE | 轮班周期定义（每店每岗一套） | clinic_id、role_type、cycle_length、daily_headcount、anchor_date |
| ROSTER_MEMBER | 轮班成员与次序 | cycle_id、seq_no、staff_id |
| SLOT_TEMPLATE | 可预约时段模板（每店 5 段） | slot_code、start_time、end_time、capacity(3) |
| BOOKING_SLOT | 每日号源（按日展开） | clinic_id、slot_date、slot_code、capacity |
| CHECKIN | 到诊签到 | appointment_id、arrived_at、taken_by_account_id |
| NOTIFICATION | 提醒与通知 | event_type、ref_type/ref_id、scheduled_at、status |
| PERFORMANCE_RULE | 绩效规则（不写死在代码里） | rule_type、value、effective_from |

## 3. 约束与触发器（已在库验证生效）

| 约束 | 实现 | 验证结果 |
|---|---|---|
| 时段满 3 人不可再约 | BEFORE INSERT/UPDATE 触发器 `fn_slot_capacity_guard` | 第 4 人被拒，提示"该时段已约满" |
| 取消/改约立即释放名额 | 取消即不计入占用（视图实时聚合） | 取消后余量 0→1 |
| 同患者不可跨店同时段预约 | EXCLUDE 排他约束 `ex_appt_patient_overlap` | 跨店同时段插入被拒 |
| 同医生不可同日不同诊所 | 唯一索引 `ux_shift_staff_day` | 跨店重复排班被拒 |

## 4. 已灌入的数据

| 项 | 数量 | 说明 |
|---|---|---|
| 诊所 | 2 | ADC 天河诊所、ADC 越秀诊所 |
| 员工 | 17 | 每店医生 5 名、前台 2 名；管理层 3 名（不按店划分，统一管两家店） |
| 登录账号 | 21 | 新增 7 个员工账号，密码沿用 `AdcDemo@2026!` |
| 排班 | 121 条 | 覆盖 2026-10-26 ~ 11-08，含医生/前台两套轮换 |
| 号源 | 140 条 | 两店 × 14 天 × 5 时段，每时段容量 3 |
| 演示调班/请假 | 4 条 | 2 条待审（1 请假、1 对调）、2 条已实施（同店顶班、跨店顶班各 1） |
| 演示预约 | 13 条 | 含一个满员时段（天河 10/27 上午 08:00-10:00 已 3/3） |
| 提醒 | 13 条 | 就诊前 12 小时触发 |
| 绩效规则 | 3 条 | 满勤 +300、请假 −100/天、顶班 +80/次 |

### 轮换结果示例（天河诊所）

| 周几 | 在岗（3/5） | 休息（2/5） |
|---|---|---|
| 周一 | 周医生、吴医生、郑医生 | 林医生、徐医生 |
| 周二 | 吴医生、郑医生、林医生 | 徐医生、周医生 |
| 周三 | 郑医生、林医生、徐医生 | 周医生、吴医生 |
| 周四 | 周医生、林医生、徐医生 | 吴医生、郑医生 |
| 周五 | 周医生、吴医生、徐医生 | 郑医生、林医生 |
| 下周一 | 郑医生、林医生、徐医生 | 周医生、吴医生 |

> 与需求示例（周一甲乙丙 / 周二乙丙丁 / …… / 下周一丙丁戊）完全一致。越秀诊所同规则，前台为 2 人隔日轮换。

## 5. 视图

| 视图 | 用途 |
|---|---|
| V_SLOT_AVAILABILITY | 患者端"未来一星期可预约查询"：每诊所/日期/时段 → 容量、已约、余量、是否满、当日值班医生 |
| V_CLINIC_DASHBOARD | 管理者看板：预约数、到诊数、爽约数、值班医生/前台数、营业额、成本、利润 |

## 6. 脚本清单（可重复执行）

```
系统编码搭建（workbuddy）/db/
├─ V3__adc_roster_booking_performance.sql   字段扩展 + 新增表
├─ V3_part2_data.sql                         人员扩充、轮班周期、时段模板
├─ V3_part3_roster.sql                       排班生成、号源、约束与触发器
├─ V3_part4_demo.sql                         演示调班/请假与顶班
├─ V3_part5_booking.sql                      演示预约、提醒、绩效、视图
├─ V4__role_consolidation_manager_only.sql   取消店长、统一为管理层
├─ V5__scope_and_capacity_alignment.sql      医生范围统一 + 容量/利润口径固化（本次）
└─ finish_seed.py                            耗材成本、还原测试数据
```

已在 `flyway_schema_history` 登记版本 `3.0.1`，避免与后续 Flyway 迁移冲突。

## 7. 待代码层实现（数据库已就绪）

- 患者端：未来 7 天时段查询（读 V_SLOT_AVAILABILITY）、预约、改约、取消
- 医生端：本周排班、当日预约、病历与处方、调班申请、绩效（含顶班/请假）
- 前台端：预约管理、到诊签到（写 CHECKIN）、生成收款二维码
- 管理层端：两店看板（读 V_CLINIC_DASHBOARD）、调班/请假审核、绩效确认
- 定时任务：扫描 NOTIFICATION（scheduled_at ≤ now 且 status=PENDING）发送提醒

### 7.1 V4 角色收敛后待同步的代码点（当前未改）

| 文件 | 现状 | 需改为 |
|---|---|---|
| `backend/.../auth/CurrentUser.java` | `isHq()` 判断 `HQ_MANAGER` | `MANAGER` |
| `backend/.../inventory/InventoryController.java` | `requireRole("RECEPTION","CLINIC_MANAGER","HQ_MANAGER")` | `requireRole("RECEPTION","MANAGER")` |
| `backend/.../performance/PerformanceController.java` | `hasRole("CLINIC_MANAGER")` 分支 | `hasRole("MANAGER")` |
| `frontend/src/api.js` | 角色中文名含「集团管理员」「诊所负责人」 | 合并为「管理层」 |
| `frontend/src/App.vue` | 菜单按 `CLINIC_MANAGER`/`HQ_MANAGER` 判定 | 统一 `MANAGER` |
