package com.adc.dental.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;

    public RegistrationController(JdbcTemplate jdbc, JwtService jwtService) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
    }

    /** 注册新患者账号：创建 PATIENT + ACCOUNT + ACCESS_GRANT(PATIENT/SELF)，并直接返回登录 token。 */
    @PostMapping("/register-patient")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest r) {
        if (jdbc.queryForObject("SELECT count(*) FROM \"ACCOUNT\" WHERE \"login_name\"=?", Integer.class, r.loginName()) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录账号已存在");
        Long patientId = jdbc.queryForObject("""
            INSERT INTO "PATIENT" ("master_no","name","birth_date","phone","email","allergy_assessment")
            VALUES ('PENDING-' || gen_random_uuid(), ?, ?, ?, ?, 'UNKNOWN')
            RETURNING "patient_id"
            """, Long.class, r.name(), r.birthDate(), r.phone(), r.email());
        Long accountId = jdbc.queryForObject("""
            INSERT INTO "ACCOUNT" ("login_name","patient_id","active","password_hash")
            VALUES (?, ?, TRUE, crypt(?, gen_salt('bf'))) RETURNING "account_id"
            """, Long.class, r.loginName(), patientId, r.password());
        jdbc.update("INSERT INTO \"ACCESS_GRANT\" (\"account_id\",\"role_code\",\"scope_type\") VALUES (?, 'PATIENT', 'SELF')", accountId);
        CurrentUser user = new CurrentUser(accountId, r.loginName(), null, patientId,
                List.of(new CurrentUser.Grant("PATIENT", "SELF", null)));
        return Map.of(
                "registered", true,
                "token", jwtService.issue(user),
                "accountId", accountId,
                "patientId", patientId,
                "loginName", r.loginName(),
                "roles", user.roleCodes(),
                "grants", List.of(Map.of("role_code", "PATIENT", "scope_type", "SELF", "clinic_id", "")));
    }

    public record RegisterRequest(@NotBlank String loginName, @NotBlank String password,
                                  @NotBlank String name, @NotBlank String phone,
                                  String email, LocalDate birthDate) {}
}
