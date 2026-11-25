# ADC 数据表目录

## 患者与预约

1. `PATIENT` 患者
2. `ALLERGEN` 过敏原
3. `PATIENT_ALLERGY` 患者过敏史
4. `APPOINTMENT` 预约
5. `APPOINTMENT_REQUEST_ITEM` 预约治疗需求
6. `APPOINTMENT_EVENT` 预约变更轨迹

## 人员与资质

7. `STAFF` 员工
8. `CLINIC` 诊所
9. `DENTIST` 牙医
10. `PRACTICE` 跨诊所执业关系
11. `QUALIFICATION` 专业资质
12. `DENTIST_QUALIFICATION` 牙医资质证书
13. `TREATMENT_REQUIRED_QUALIFICATION` 治疗所需资质

## 排班与调班审批

14. `SHIFT` 排班版本
15. `SHIFT_REQUEST` 调班休假申请
16. `SHIFT_REQUEST_ITEM` 申请涉及班次
17. `REQUEST_APPOINTMENT_IMPACT` 受影响预约处理项

## 诊疗与价格

18. `TREATMENT_ITEM` 标准治疗项目
19. `ENCOUNTER` 就诊
20. `PRICE_VERSION` 标准单位价格版本
21. `TREATMENT_LINE` 实际治疗明细

## 治疗后结算

22. `INVOICE` 患者结算单
23. `INVOICE_VERSION` 结算单历史版本
24. `INVOICE_LINE` 结算逐行明细
25. `PAYMENT` 患者收款记录

## 耗材库存

26. `MATERIAL` 药品及耗材
27. `CLINIC_STOCK` 诊所物料库存
28. `TREATMENT_MATERIAL_USAGE` 治疗实际耗材
29. `STOCK_MOVEMENT` 库存数量流水

## 权限与审计

30. `ACCOUNT` 访问账号
31. `ACCESS_GRANT` 角色与数据范围
32. `AUDIT_EVENT` 敏感操作审计

## 运营绩效

33. `DENTIST_PERFORMANCE` 牙医绩效结果

