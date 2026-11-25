package com.adc.dental.portal;

import com.adc.dental.auth.AccessGuard;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 医生工作台：本周排班、当日预约（含患者基础医疗信息）、患者档案摘要、当月绩效。
 *
 * 医生的可见范围统一为 CLINIC（归属店）；跨店顶班日由 SHIFT.source='SUBSTITUTE' 记录，
 * 排班查询按 shift 本身归属的诊所返回，因此顶班日在哪家出诊就能看到哪家的当日预约。
 */
@RestController
@RequestMapping("/api/portal/dentist")
public class DentistPortalController {

    private final JdbcTemplate jdbc;

    public DentistPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 本周（周一~周日）排班：哪天上班、在哪家店、是否顶班、同班同事。 */
    @GetMapping("/roster")
    public Map<String, Object> roster(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        Long staffId = AccessGuard.requireRole("DENTIST").staffId();
        if (staffId == null) throw new IllegalStateException("医生账号未绑定员工档案");
        LocalDate from = (weekOf == null ? LocalDate.now() : weekOf).with(java.time.DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);
        List<Map<String, Object>> shifts = jdbc.queryForList("""
                SELECT s."shift_date", s."shift_id", s."clinic_id", cl."name" AS clinic_name,
                       s."source", s."substitute_from",
                       (SELECT string_agg(st2."name", '、' ORDER BY st2."staff_id")
                          FROM "SHIFT" s2 JOIN "STAFF" st2 ON st2."staff_id" = s2."staff_id"
                         WHERE s2."clinic_id" = s."clinic_id" AND s2."shift_date" = s."shift_date"
                           AND s2."role_type" = 'DENTIST' AND s2."status" = 'PUBLISHED') AS colleagues,
                       (SELECT string_agg(rc."name", '、')
                          FROM "SHIFT" s3 JOIN "STAFF" rc ON rc."staff_id" = s3."staff_id"
                         WHERE s3."clinic_id" = s."clinic_id" AND s3."shift_date" = s."shift_date"
                           AND s3."role_type" = 'RECEPTION' AND s3."status" = 'PUBLISHED') AS reception,
                       to_char(s."shift_date", 'Dy') AS weekday_cn
                FROM "SHIFT" s JOIN "CLINIC" cl ON cl."clinic_id" = s."clinic_id"
                WHERE s."staff_id" = ? AND s."role_type" = 'DENTIST' AND s."status" = 'PUBLISHED'
                  AND s."shift_date" BETWEEN ? AND ?
                ORDER BY s."shift_date"
                """, staffId, from, to);
        return Map.of("weekStart", from.toString(), "weekEnd", to.toString(), "shifts", shifts,
                "today", LocalDate.now().toString());
    }

    /** 某日出诊的预约列表（含患者过敏信息），默认今天。 */
    @GetMapping("/appointments")
    public List<Map<String, Object>> dayAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long staffId = AccessGuard.requireRole("DENTIST").staffId();
        if (staffId == null) throw new IllegalStateException("医生账号未绑定员工档案");
        LocalDate day = date == null ? LocalDate.now() : date;
        return jdbc.queryForList("""
                SELECT a."appointment_id", a."booking_no", a."status", a."starts_at", a."ends_at",
                       a."slot_code", st."slot_name", a."clinic_id", cl."name" AS clinic_name,
                       p."patient_id", p."name" AS patient_name, p."phone", p."birth_date",
                       p."special_needs", p."allergy_assessment",
                       COALESCE((SELECT string_agg(al."name" || '(' || pa."reaction" || ')', '、')
                                   FROM "PATIENT_ALLERGY" pa JOIN "ALLERGEN" al ON al."allergen_id" = pa."allergen_id"
                                  WHERE pa."patient_id" = p."patient_id"), '无记录') AS allergies,
                       (SELECT count(*) FROM "ENCOUNTER" e WHERE e."appointment_id" = a."appointment_id") AS encounter_count
                FROM "APPOINTMENT" a
                JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = a."clinic_id"
                LEFT JOIN "SLOT_TEMPLATE" st ON st."clinic_id" = a."clinic_id" AND st."slot_code" = a."slot_code"
                WHERE a."shift_id" IN (SELECT "shift_id" FROM "SHIFT"
                                        WHERE "staff_id" = ? AND "shift_date" = ? AND "status" = 'PUBLISHED')
                  AND a."status" NOT IN ('CANCELLED')
                ORDER BY a."starts_at", a."appointment_id"
                """, staffId, day);
    }

    /** 患者档案摘要（医生接诊时查看过敏史与既往就诊）。 */
    @GetMapping("/patients/{patientId}/summary")
    public Map<String, Object> patientSummary(@PathVariable long patientId) {
        AccessGuard.requireRole("DENTIST", "RECEPTION", "MANAGER");
        Map<String, Object> profile = jdbc.queryForMap("""
                SELECT "patient_id","master_no","name","birth_date","phone","special_needs","allergy_assessment"
                FROM "PATIENT" WHERE "patient_id"=?
                """, patientId);
        List<Map<String, Object>> allergies = jdbc.queryForList("""
                SELECT al."name" AS allergen, pa."reaction", pa."notes"
                FROM "PATIENT_ALLERGY" pa JOIN "ALLERGEN" al ON al."allergen_id" = pa."allergen_id"
                WHERE pa."patient_id"=? ORDER BY al."name"
                """, patientId);
        List<Map<String, Object>> history = jdbc.queryForList("""
                SELECT e."encounter_id", e."arrived_at", e."chief_complaint", e."diagnosis",
                       tl."name_snapshot", tl."quantity", tl."unit_price_snapshot"
                FROM "ENCOUNTER" e
                LEFT JOIN "TREATMENT_LINE" tl ON tl."encounter_id" = e."encounter_id"
                JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
                WHERE a."patient_id"=? ORDER BY e."arrived_at" DESC LIMIT 50
                """, patientId);
        return Map.of("profile", profile, "allergies", allergies, "history", history);
    }

    /** 当月绩效：出勤、请假、调班、顶班与最终得分。 */
    @GetMapping("/performance")
    public Map<String, Object> performance(@RequestParam(required = false) String month) {
        Long staffId = AccessGuard.requireRole("DENTIST").staffId();
        if (staffId == null) throw new IllegalStateException("医生账号未绑定员工档案");
        String periodStart = resolveMonthStart(month);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dp."performance_id", dp."period_start", dp."period_end",
                       dp."scheduled_days", dp."leave_days", dp."swap_applied_count", dp."cover_shift_count",
                       dp."full_attendance", dp."attendance_bonus", dp."leave_penalty", dp."cover_bonus",
                       dp."final_score", dp."reward_level", dp."review_status",
                       dp."completed_count", dp."treatment_count", dp."performance_score"
                FROM "DENTIST_PERFORMANCE" dp
                WHERE dp."dentist_id" = ? AND dp."period_start" = ?
                """, staffId, LocalDate.parse(periodStart));
        Map<String, Object> perf = rows.isEmpty() ? Map.of("note", "本期尚未生成绩效") : rows.getFirst();
        List<Map<String, Object>> covers = jdbc.queryForList("""
                SELECT s."shift_date", cl."name" AS clinic_name, s."substitute_from"
                FROM "SHIFT" s JOIN "CLINIC" cl ON cl."clinic_id" = s."clinic_id"
                WHERE s."staff_id" = ? AND s."source" = 'SUBSTITUTE' AND s."status" = 'PUBLISHED'
                  AND s."shift_date" >= ? AND s."shift_date" < ? + INTERVAL '1 month'
                ORDER BY s."shift_date"
                """, staffId, LocalDate.parse(periodStart), LocalDate.parse(periodStart));
        return Map.of("periodStart", periodStart, "performance", perf, "coverShifts", covers);
    }

    /** 我的调班/请假申请列表（与 ShiftRequestController 同源）。 */
    @GetMapping("/shift-requests")
    public List<Map<String, Object>> myShiftRequests() {
        Long staffId = AccessGuard.requireRole("DENTIST", "RECEPTION").staffId();
        if (staffId == null) return List.of();
        return jdbc.queryForList("""
                SELECT r."request_id", r."request_type", r."requested_date", r."swap_target_date",
                       r."reason", r."status", r."submitted_at", r."reviewed_at", r."rejection_reason"
                FROM "SHIFT_REQUEST" r
                WHERE r."applicant_staff_id" = ?
                ORDER BY r."request_id" DESC
                """, staffId);
    }

    private String resolveMonthStart(String month) {
        if (month == null || month.isBlank()) {
            LocalDate now = LocalDate.now();
            return now.withDayOfMonth(1).toString();
        }
        if (month.matches("\\d{4}-\\d{2}")) return month + "-01";
        try {
            return LocalDate.parse(month).withDayOfMonth(1).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("month 需形如 2026-10");
        }
    }

    public record PrescriptionRequest(@NotBlank String content) {}
}
