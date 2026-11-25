# OpenDental 到 ADC 的参考映射

| OpenDental 参考表 | ADC 表 | 处理方式 |
|---|---|---|
| `patient` | `PATIENT` | 保留患者主数据；按 ADC 需求精简字段 |
| `allergy`、`allergydef` | `ALLERGEN`、`PATIENT_ALLERGY` | 保留过敏原字典与患者关联分离 |
| `appointment` | `APPOINTMENT` | 保留患者、牙医、诊所、状态和时间区间 |
| `histappointment` | `APPOINTMENT_EVENT` | 用事件表记录改约、取消和状态变化 |
| `schedule` | `SHIFT` | 改造为带版本的发布排班 |
| `provider` | `DENTIST`、`STAFF` | 分离员工主体和牙医子类型 |
| `providercliniclink` | `PRACTICE` | 建模牙医与诊所的多对多执业关系 |
| `clinic` | `CLINIC` | 保留多诊所基础信息 |
| `procedurecode` | `TREATMENT_ITEM` | 使用 ADC 的标准治疗项目编码 |
| `procedurelog` | `TREATMENT_LINE`、`ENCOUNTER` | 拆分就诊事实与实际治疗明细 |
| `payment` | `PAYMENT` | 仅保留患者收款，不复制保险支付和退款体系 |
| `securitylog` | `AUDIT_EVENT` | 保留敏感操作审计思想 |
| `evaluation*` | `DENTIST_PERFORMANCE` | 不直接采用；教学评价改为 ADC 的简单运营绩效快照 |
| `payperiod` | 无直接对应 | 不引入工资和考勤业务 |
| `claim`、`insplan` | 无直接对应 | 不纳入保险理赔 |
| `account`、`transaction` | 无直接对应 | 不纳入 OpenDental 会计总账 |

OpenDental XML 提供了表说明、字段类型和部分外键标识；ADC 的主键、唯一约束、状态枚举和业务规则仍以本项目需求为准。

