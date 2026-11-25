# 牙医绩效指标口径

`DENTIST_PERFORMANCE` 只保存运营统计结果，不承担财务结算。

| 指标 | 计算口径 |
|---|---|
| `appointment_count` | 统计周期内、计划开始时间已到且属于该牙医/诊所的去重预约数 |
| `attended_count` | 上述预约中形成有效到诊或就诊事实的数量 |
| `cancelled_count` | 上述预约中最终状态为 `CANCELLED` 的数量 |
| `no_show_count` | 上述预约中最终状态为 `NO_SHOW` 的数量 |
| `completed_count` | 上述预约中形成 `COMPLETED` 就诊的数量 |
| `treatment_count` | 上述完成就诊关联的实际治疗明细行数 |
| `workload_target_count` | 该牙医在该诊所、该统计周期的治疗项目数量目标；这是工作量目标，不是金额 |
| `performance_score` | 加权 KPI 得分；公式版本写入 `calculation_version` |
| `reward_level` | 根据审核后的工作量结果标记 `NONE`、`NORMAL`、`GOOD` 或 `EXCELLENT`，不保存金额 |

指标快照计算时必须记录 `calculated_at`。如果后续调整统计公式，使用新的 `calculation_version`，不覆盖旧版本结果。

## 评分公式

采用常见的加权 KPI 评分法，满分 100 分：

```text
到诊率 = 到诊数量 / 预约数量 × 100
完成率 = 完成就诊数量 / 预约数量 × 100
工作量达成率 = min(治疗项目数量 / 周期治疗项目目标数 × 100, 100)

绩效得分 = 到诊率 × 40%
         + 完成率 × 40%
         + 工作量达成率 × 20%
```

预约数量为 0 时，绩效得分记为 `NULL`，不评定奖励等级。工作量目标为 0 时，工作量达成率按 0 计算，并在结果中提示目标未配置。

奖励等级建议：

- `NONE`：低于 60 分或无有效样本；
- `NORMAL`：60 至 74.99 分；
- `GOOD`：75 至 89.99 分；
- `EXCELLENT`：90 至 100 分。

