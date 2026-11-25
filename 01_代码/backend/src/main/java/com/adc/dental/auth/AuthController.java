package com.adc.dental.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, JwtService jwtService) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        List<Map<String, Object>> accounts = jdbc.queryForList("""
                SELECT a."account_id", a."login_name", a."staff_id", a."patient_id",
                       g."role_code", g."scope_type", g."clinic_id"
                FROM "ACCOUNT" a
                LEFT JOIN "ACCESS_GRANT" g ON g."account_id" = a."account_id"
                WHERE a."login_name" = ? AND a."active" = TRUE
                  AND a."password_hash" = crypt(?, a."password_hash")
                ORDER BY g."grant_id"
                """, request.loginName(), request.password());
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        Map<String, Object> first = accounts.getFirst();
        CurrentUser user = toCurrentUser(first, accounts);
        jdbc.update("UPDATE \"ACCOUNT\" SET \"last_login_at\" = ? WHERE \"account_id\" = ?",
                Timestamp.from(OffsetDateTime.now().toInstant()), user.accountId());
        return Map.of(
                "token", jwtService.issue(user),
                "accountId", user.accountId(),
                "loginName", user.loginName(),
                "staffId", user.staffId() == null ? "" : user.staffId(),
                "patientId", user.patientId() == null ? "" : user.patientId(),
                "roles", user.roleCodes(),
                "grants", accounts);
    }

    /** 前端刷新页面后凭 token 恢复登录态。 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        CurrentUser user = AccessGuard.user();
        return Map.of(
                "accountId", user.accountId(),
                "loginName", user.loginName(),
                "staffId", user.staffId() == null ? "" : user.staffId(),
                "patientId", user.patientId() == null ? "" : user.patientId(),
                "roles", user.roleCodes(),
                "grants", user.grants().stream().map(g -> Map.of(
                        "role_code", g.roleCode(),
                        "scope_type", g.scopeType(),
                        "clinic_id", g.clinicId() == null ? "" : g.clinicId())).toList());
    }

    static CurrentUser toCurrentUser(Map<String, Object> first, List<Map<String, Object>> grantRows) {
        List<CurrentUser.Grant> grants = new ArrayList<>();
        for (Map<String, Object> row : grantRows) {
            if (row.get("role_code") == null) continue;
            grants.add(new CurrentUser.Grant(
                    String.valueOf(row.get("role_code")),
                    String.valueOf(row.get("scope_type")),
                    row.get("clinic_id") == null ? null : ((Number) row.get("clinic_id")).longValue()));
        }
        return new CurrentUser(
                ((Number) first.get("account_id")).longValue(),
                String.valueOf(first.get("login_name")),
                first.get("staff_id") == null ? null : ((Number) first.get("staff_id")).longValue(),
                first.get("patient_id") == null ? null : ((Number) first.get("patient_id")).longValue(),
                grants);
    }

    public record LoginRequest(@NotBlank String loginName, @NotBlank String password) {
    }
}
