# ADC 牙科诊所管理系统

中南大学《系统分析与设计》课程项目（2026 年秋）。

## 在线演示

- Web 系统：<http://119.29.188.107/>
- 微信小程序：扫码打开（小程序码图片见仓库根目录）

## 目录结构

| 目录 | 内容 |
|---|---|
| `01_代码/backend` | Spring Boot 后端源码（Java 21 + JDBC + Flyway + JWT） |
| `01_代码/frontend` | Vue 3 + Vite 前端源码 |
| `02_数据库/migrations` | 数据库迁移脚本 V1–V5（PostgreSQL） |
| `02_数据库/设计文档` | 数据字典、数据库总图、设计说明 |
| `03_需求文件` | 牙科诊所需求文件终版.pdf |

## 本地运行

1. 准备 PostgreSQL，创建数据库 `adc_dental`。
2. 复制 `.env.example` 为 `.env`，填写真实数据库口令与 JWT 密钥（不要提交 `.env`）。
3. 后端：进入 `01_代码/backend`，执行 `mvn spring-boot:run`。
4. 前端：进入 `01_代码/frontend`，执行 `npm install` 后 `npm run dev`。

## 演示账号

统一登录密码：`AdcDemo@2026!`

| 角色 | 账号 |
|---|---|
| 管理层 | `hq_manager`、`manager_a`、`manager_b` |
| 前台 | `reception_a`、`reception_b`、`reception_a2`、`reception_b2` |
| 医生 | `dentist_zhou`、`dentist_wu`、`dentist_zheng`、`dentist_liu`、`dentist_sun`、`dentist_lin`、`dentist_xu`、`dentist_huang`、`dentist_qiu`、`dentist_feng` |
| 患者 | `patient_lin`、`patient_chen` |

完整登录信息（含线上系统二维码、小程序二维码）见 `登录.docx`。
