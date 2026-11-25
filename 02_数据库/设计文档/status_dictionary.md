# 状态与枚举字典

| 表 | 字段 | 允许值 | 说明 |
|---|---|---|---|
| PATIENT | allergy_assessment | `UNKNOWN`, `NONE_KNOWN`, `RECORDED` | 过敏评估状态 |
| PRACTICE | status | `ACTIVE`, `SUSPENDED`, `ENDED` | 跨诊所执业关系 |
| SHIFT | status | `DRAFT`, `PUBLISHED`, `SUPERSEDED`, `CANCELLED` | 排班版本状态 |
| APPOINTMENT | status | `BOOKED`, `CONFIRMED`, `CANCELLED`, `ARRIVED`, `NO_SHOW`, `COMPLETED` | 预约状态 |
| ENCOUNTER | status | `ARRIVED`, `IN_PROGRESS`, `COMPLETED`, `VOID` | 就诊状态 |
| INVOICE | status | `DRAFT`, `CONFIRMED`, `VOID`, `PAID` | 结算单状态 |
| PAYMENT | method | `CASH`, `BANK_CARD`, `MOBILE_PAYMENT` | 患者收款方式 |
| PAYMENT | status | `SUCCESS`, `PENDING`, `FAILED` | 收款处理状态 |
| SHIFT_REQUEST | request_type | `SHIFT_CHANGE`, `LEAVE`, `OTHER` | 调班申请类型 |
| SHIFT_REQUEST | status | `SUBMITTED`, `REJECTED`, `COORDINATING`, `IMPLEMENTED` | 申请处理状态 |
| REQUEST_APPOINTMENT_IMPACT | resolution_status | `PENDING`, `CONTACTED`, `CONFIRMED`, `RESOLVED` | 受影响预约处理状态 |
| STOCK_MOVEMENT | movement_type | `INBOUND`, `USAGE`, `ADJUSTMENT` | 库存数量变化类型 |
| ACCESS_GRANT | role_code | `PATIENT`, `RECEPTION`, `DENTIST`, `CLINIC_MANAGER`, `HQ_MANAGER` | 角色 |
| ACCESS_GRANT | scope_type | `SELF`, `CLINIC`, `CHAIN` | 数据范围 |
| DENTIST_PERFORMANCE | reward_level | `NONE`, `NORMAL`, `GOOD`, `EXCELLENT` | 奖励等级，不是金额 |
| DENTIST_PERFORMANCE | review_status | `DRAFT`, `CONFIRMED`, `ARCHIVED` | 绩效结果审核状态 |

`APPOINTMENT_EVENT.old_status` 和 `new_status` 使用 `APPOINTMENT.status` 的同一组值。状态转换是否合法由服务层和数据库检查共同保证，不能仅依靠字段类型名称。

