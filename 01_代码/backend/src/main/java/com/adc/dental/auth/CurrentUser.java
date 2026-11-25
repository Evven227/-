package com.adc.dental.auth;

import java.util.List;

/**
 * 当前登录用户上下文，由 JWT 解析后注入。
 * grants 对应 ACCOUNT 的 ACCESS_GRANT 授权行（一个账号可有多角色）。
 */
public record CurrentUser(Long accountId, String loginName, Long staffId, Long patientId,
                          List<Grant> grants) {

    public record Grant(String roleCode, String scopeType, Long clinicId) {}

    public boolean hasRole(String... roles) {
        for (String role : roles) {
            if (grants.stream().anyMatch(g -> role.equals(g.roleCode()))) return true;
        }
        return false;
    }

    public boolean isPatient() {
        return hasRole("PATIENT");
    }

    public boolean isHq() {
        return hasRole("MANAGER");
    }

    /** 员工类角色（前台/医生/管理层）的诊所范围；无则返回 null。 */
    public Long clinicId() {
        return grants.stream().filter(g -> g.clinicId() != null).map(Grant::clinicId).findFirst().orElse(null);
    }

    /** 患者角色绑定的患者档案 ID；非患者账号返回 null。 */
    public Long patientId() {
        return patientId;
    }

    public List<String> roleCodes() {
        return grants.stream().map(Grant::roleCode).distinct().sorted().toList();
    }
}
