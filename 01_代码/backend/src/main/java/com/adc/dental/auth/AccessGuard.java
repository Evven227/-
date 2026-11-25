package com.adc.dental.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 角色与数据范围校验助手。所有控制器统一通过本类获取当前用户并做越权检查。
 *
 * 数据范围约定（与 ACCESS_GRANT.scope_type 对应）：
 * - SELF    ：患者本人数据
 * - CLINIC  ：所在诊所数据（clinic_id 限定）
 * - CHAIN   ：集团全局数据
 */
public final class AccessGuard {

    private AccessGuard() {
    }

    /** 取当前登录用户；未登录返回 401。 */
    public static CurrentUser user() {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }

    /** 要求当前用户拥有任一指定角色，否则 403。 */
    public static CurrentUser requireRole(String... roles) {
        CurrentUser user = user();
        if (!user.hasRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权执行该操作");
        }
        return user;
    }

    /** 当前账号的 staff_id（员工类账号），无则 null。 */
    public static Long staffId() {
        return user().staffId();
    }

    /** 患者账号绑定的 patient_id；非患者返回 null。 */
    public static Long ownPatientId() {
        return user().patientId();
    }

    /** 患者账号必须存在绑定档案，否则 403（防止患者角色但未绑定档案的脏数据）。 */
    public static Long requireOwnPatientId() {
        Long id = ownPatientId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定患者档案");
        }
        return id;
    }

    /** 员工账号的诊所范围；管理层或无诊所范围返回 null（表示不限制）。 */
    public static Long ownClinicId() {
        CurrentUser user = user();
        if (user.isHq()) return null;
        return user.clinicId();
    }

    /** 员工账号对应的牙医 ID（ACCOUNT.staff_id -> DENTIST.dentist_id，二者同值）；非牙医返回 null。 */
    public static Long ownDentistId(JdbcTemplate jdbc) {
        Long staffId = staffId();
        if (staffId == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"dentist_id\" FROM \"DENTIST\" WHERE \"dentist_id\" = ?", staffId);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("dentist_id")).longValue();
    }
}
