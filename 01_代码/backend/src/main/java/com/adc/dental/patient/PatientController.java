package com.adc.dental.patient;

import com.adc.dental.auth.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final JdbcTemplate jdbc;

    public PatientController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 患者列表（按数据范围过滤）：
     * - 患者账号：只能看自己的档案；
     * - 诊所员工：看本诊所预约过的患者；
     * - 管理层：看全部。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        AccessGuard.user();
        Long ownPatientId = AccessGuard.ownPatientId();
        if (ownPatientId != null) {
            return jdbc.queryForList("""
                    SELECT "patient_id", "master_no", "name", "birth_date", "phone",
                           "email", "allergy_assessment"
                    FROM "PATIENT"
                    WHERE "merged_into_patient_id" IS NULL AND "patient_id" = ?
                    ORDER BY "patient_id"
                    """, ownPatientId);
        }
        Long clinicId = AccessGuard.ownClinicId();
        if (clinicId != null) {
            return jdbc.queryForList("""
                    SELECT DISTINCT p."patient_id", p."master_no", p."name", p."birth_date", p."phone",
                           p."email", p."allergy_assessment"
                    FROM "PATIENT" p
                    JOIN "APPOINTMENT" a ON a."patient_id" = p."patient_id"
                    JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
                    WHERE p."merged_into_patient_id" IS NULL AND s."clinic_id" = ?
                    ORDER BY p."patient_id"
                    """, clinicId);
        }
        return jdbc.queryForList("""
                SELECT "patient_id", "master_no", "name", "birth_date", "phone",
                       "email", "allergy_assessment"
                FROM "PATIENT"
                WHERE "merged_into_patient_id" IS NULL
                ORDER BY "patient_id"
                """);
    }
}
