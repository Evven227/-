# ADC 牙科诊所数据库架构

## 1. 数据域

```text
身份与权限
  ├─ 患者档案与过敏史
  └─ 账号、授权、审计

诊所运营
  ├─ 诊所、员工、牙医
  ├─ 牙医资质与跨诊所执业
  └─ 排班、调班休假、预约影响处理

医疗业务
  ├─ 预约与预约事件
  ├─ 到诊与就诊
  ├─ 标准治疗项目与价格版本
  └─ 实际治疗与耗材使用

患者结算
  ├─ 结算单
  ├─ 结算版本与逐行费用
  └─ 患者收款

运营分析
  ├─ 预约、到诊、取消和工作量的派生指标
  └─ 牙医绩效周期快照
```

## 2. 核心数据链路

```text
PATIENT
  → APPOINTMENT
  → ENCOUNTER
  → TREATMENT_LINE
  → INVOICE
  → INVOICE_VERSION
  → PAYMENT
```

排班侧通过 `SHIFT` 连接 `DENTIST` 和 `CLINIC`，预约通过 `shift_id` 占用已发布时段。资质侧通过 `PRACTICE`、`DENTIST_QUALIFICATION` 和 `TREATMENT_REQUIRED_QUALIFICATION` 支持预约匹配。

## 3. 绩效边界

`DENTIST_PERFORMANCE` 是周期关闭后的运营结果快照。实时指标由预约、就诊和治疗明细计算，快照用于审核和历史查看。它不替代结算，也不记录工资、利润、成本或奖金支付。

每条绩效结果还保存 `calculation_version` 和 `calculated_at`，用于解释同一周期不同版本的指标口径；绩效表不是财务账本。

