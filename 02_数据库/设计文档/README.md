# ADC 牙科诊所数据库设计

本目录是 ADC 牙科诊所管理系统的数据库设计交付包。设计以现有需求分析、32 张核心表 ER 模型和 OpenDental 26.2 数据库文档为参考，物理数据库采用 PostgreSQL 18，但不复制 OpenDental 的保险、理赔、会计、处方和复杂支付模块。

## 当前范围

- 患者主档案、过敏史和跨诊所识别
- 诊所、员工、牙医资质和跨诊所执业
- 预约、改约、取消和预约变更轨迹
- 排班版本、调班休假申请和受影响预约处理
- 到诊、就诊、实际治疗和标准治疗项目
- 治疗后结算、部分收款和收款凭据
- 药品与耗材的数量库存和治疗耗用
- 账号、角色授权和敏感操作审计
- 牙医周期性运营绩效结果

## 明确排除

不设计退款、保险理赔、工资、奖金发放、利润、医疗用品成本、采购金额、会计总账或第三方支付网关。

## 文件索引

| 目录或文件 | 用途 |
|---|---|
| `spec.md` | 数据库设计需求基线 |
| `plan.md` | 设计实施计划和验收标准 |
| `architecture.md` | 数据域、分层和依赖架构 |
| `scope_decisions.md` | 范围决策记录 |
| `opendental_mapping.md` | OpenDental 到 ADC 的参考映射 |
| `table_catalog.md` | 33 张正式数据表目录 |
| `data_type_notes.md` | 日期、日期时间、事件时间和金额数量类型约定 |
| `status_dictionary.md` | 状态字段允许值和转换说明 |
| `dbms_decision.md` | 逻辑模型与 PostgreSQL 18 物理方言边界 |
| `审查修订记录.md` | 本轮反思、修订和待确认事项 |
| `performance_metric_definition.md` | 绩效指标口径和版本规则 |
| `concurrency_policy.md` | 预约、库存和收款的并发控制说明 |
| `数据库总图.puml/png/svg` | 33 张表的完整 ER 总图 |
| `数据字典.json` | 字段、主键、唯一键和外键定义 |
| `sql/` | 关键物理模型 SQL 和绩效查询视图 |
| `tools/` | 可重复生成和校验脚本 |

## 生成

在 PowerShell 中执行：

```powershell
python 'E:\系统分析设计\数据库设计\tools\generate_database_package.py'
$jar = 'E:\系统分析设计\10月11日\plantuml.jar'
java -Xmx2g -DPLANTUML_LIMIT_SIZE=32768 -jar $jar -charset UTF-8 -failfast2 -tpng 'E:\系统分析设计\数据库设计\数据库总图.puml'
java -Xmx2g -DPLANTUML_LIMIT_SIZE=32768 -jar $jar -charset UTF-8 -failfast2 -tsvg 'E:\系统分析设计\数据库设计\数据库总图.puml'
```

