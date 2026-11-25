# SQL 文件说明

当前 SQL 目录包含覆盖 33 张表的 PostgreSQL 18 逻辑建表基线，以及绩效表、绩效查询视图和范围约束说明。建表基线仍需结合实际部署环境补充迁移版本和并发冲突处理，不能直接当作已部署的生产数据库。

推荐执行顺序：

1. `00_schema_postgresql18.sql`；
2. `01_dentist_performance.sql` 中的专项约束（如已在基线中执行则合并）；
3. `02_dentist_performance_view.sql`；
4. `03_scope_notes.sql`；
5. `04_demo_seed.sql`，导入覆盖患者、牙医、诊所负责人、前台、账号、预约、就诊、结算、收款、库存和绩效的演示数据；
6. `06_database_tests.sql`，执行结构、主链路、预约冲突、库存、重复收款和绩效规则测试。

