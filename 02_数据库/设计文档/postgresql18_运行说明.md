# PostgreSQL 18 运行说明

## 已验证环境

- PostgreSQL 版本：18.6
- 程序目录：`D:\PostgreSQL\18`
- 数据目录：`D:\ADCPostgreSQL18\data`
- 主机：`127.0.0.1`
- 端口：`5433`
- 数据库：`adc_dental`
- 管理员账号：`postgres`

密码已在交付消息中单独告知，不写入项目文件。

## 启动

```powershell
& 'D:\PostgreSQL\18\bin\pg_ctl.exe' -D 'D:\ADCPostgreSQL18\data' -l 'D:\ADCPostgreSQL18\postgresql.log' -o '-p 5433' start
```

## 停止

```powershell
& 'D:\PostgreSQL\18\bin\pg_ctl.exe' -D 'D:\ADCPostgreSQL18\data' stop -m fast
```

## 连接

```powershell
$env:PGPASSWORD='（使用交付消息中的密码）'
& 'D:\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -p 5433 -U postgres -d adc_dental
```

## 验证

```powershell
& 'D:\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -p 5433 -U postgres -d adc_dental -f 'E:\系统分析设计\数据库设计\sql\05_verify_demo.sql'
```

当前 5432 端口仍有一个未纳入本次新实例的旧 PostgreSQL 进程占用，因此新实例使用 5433，避免覆盖或误操作旧数据。
