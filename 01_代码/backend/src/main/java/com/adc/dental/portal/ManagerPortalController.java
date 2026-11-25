package com.adc.dental.portal;

import com.adc.dental.auth.AccessGuard;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理层经营面板：两店预约/到诊、出勤、营业额与利润，以及调班审核所需的候选顶班人。
 *
 * 利润口径：营业额 − 耗材成本（见 V_CLINIC_DASHBOARD）。
 * 角色：MANAGER（数据范围 CHAIN，可看两家店全量）。
 */
@RestController
@RequestMapping("/api/portal/manager")
public class ManagerPortalController {

    private final JdbcTemplate jdbc;

    public ManagerPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 两店每日经营明细。 */
    @GetMapping("/dashboard")
    public List<Map<String, Object>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AccessGuard.requireRole("MANAGER");
        LocalDate a = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate b = to == null ? LocalDate.now() : to;
        return jdbc.queryForList("""
                SELECT "clinic_id","clinic_name","biz_date","booked_cnt","arrived_cnt","cancelled_cnt",
                       "noshow_cnt","duty_dentist_cnt","duty_reception_cnt","revenue","cost","profit"
                FROM "V_CLINIC_DASHBOARD"
                WHERE "biz_date" BETWEEN ? AND ?
                ORDER BY "clinic_id", "biz_date"
                """, a, b);
    }

    /** 汇总卡片：营业额、成本、利润、预约、到诊率。 */
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AccessGuard.requireRole("MANAGER");
        LocalDate a = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate b = to == null ? LocalDate.now() : to;
        Map<String, Object> total = jdbc.queryForMap("""
                SELECT COALESCE(sum("revenue"),0) AS revenue, COALESCE(sum("cost"),0) AS cost,
                       COALESCE(sum("profit"),0) AS profit, COALESCE(sum("booked_cnt"),0) AS booked,
                       COALESCE(sum("arrived_cnt"),0) AS arrived,
                       COALESCE(sum("cancelled_cnt"),0) AS cancelled,
                       COALESCE(sum("noshow_cnt"),0) AS noshow
                FROM "V_CLINIC_DASHBOARD" WHERE "biz_date" BETWEEN ? AND ?
                """, a, b);
        List<Map<String, Object>> byClinic = jdbc.queryForList("""
                SELECT "clinic_id", "clinic_name", COALESCE(sum("revenue"),0) AS revenue,
                       COALESCE(sum("cost"),0) AS cost, COALESCE(sum("profit"),0) AS profit,
                       COALESCE(sum("booked_cnt"),0) AS booked, COALESCE(sum("arrived_cnt"),0) AS arrived
                FROM "V_CLINIC_DASHBOARD" WHERE "biz_date" BETWEEN ? AND ?
                GROUP BY "clinic_id","clinic_name" ORDER BY "clinic_id"
                """, a, b);
        return Map.of("from", a.toString(), "to", b.toString(), "total", total, "byClinic", byClinic);
    }

    /** 指定日两店出勤：医生与前台值班名单。 */
    @GetMapping("/staff-on-duty")
    public List<Map<String, Object>> staffOnDuty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AccessGuard.requireRole("MANAGER");
        LocalDate day = date == null ? LocalDate.now() : date;
        return jdbc.queryForList("""
                SELECT cl."clinic_id", cl."name" AS clinic_name, s."role_type",
                       string_agg(st."name", '、' ORDER BY st."staff_id") AS staff_names,
                       count(*) AS headcount
                FROM "SHIFT" s
                JOIN "STAFF" st ON st."staff_id" = s."staff_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = s."clinic_id"
                WHERE s."shift_date" = ? AND s."status" = 'PUBLISHED'
                GROUP BY cl."clinic_id", cl."name", s."role_type"
                ORDER BY cl."clinic_id", s."role_type"
                """, day);
    }

    /** 待处理的调班/请假申请。 */
    @GetMapping("/shift-requests")
    public List<Map<String, Object>> pendingRequests(@RequestParam(required = false) String status) {
        AccessGuard.requireRole("MANAGER");
        String st = (status == null || status.isBlank()) ? "SUBMITTED" : status;
        return jdbc.queryForList("""
                SELECT r."request_id", r."request_type", r."requested_date", r."swap_target_date",
                       r."reason", r."status", r."submitted_at",
                       ap."name" AS applicant_name, ap."home_clinic_id" AS clinic_id,
                       cl."name" AS clinic_name, r."applicant_staff_id", r."applicant_dentist_id",
                       COALESCE(r."applicant_staff_id", r."applicant_dentist_id") AS staff_id,
                       os."shift_id" AS original_shift_id,
                       os."starts_at" AS original_starts_at, os."ends_at" AS original_ends_at
                FROM "SHIFT_REQUEST" r
                LEFT JOIN "STAFF" ap ON ap."staff_id" = COALESCE(r."applicant_staff_id", r."applicant_dentist_id")
                LEFT JOIN "CLINIC" cl ON cl."clinic_id" = ap."home_clinic_id"
                LEFT JOIN LATERAL (
                        SELECT s."shift_id", s."starts_at", s."ends_at" FROM "SHIFT" s
                         WHERE s."staff_id" = COALESCE(r."applicant_staff_id", r."applicant_dentist_id")
                           AND s."shift_date" = r."requested_date" AND s."status" = 'PUBLISHED'
                         ORDER BY s."shift_id" LIMIT 1) os ON TRUE
                WHERE r."status" = ?
                ORDER BY r."requested_date", r."request_id"
                """, st);
    }

    /**
     * 顶班候选人：优先同诊所当天休假的医生，再给另一家店当天休假的医生。
     * 与需求「优先安排同店休假医生，不方便再安排另一家店」一致。
     */
    @GetMapping("/shift-requests/{id}/candidates")
    public List<Map<String, Object>> candidates(@PathVariable long id) {
        AccessGuard.requireRole("MANAGER");
        Map<String, Object> req = jdbc.queryForMap("""
                SELECT r."request_id",
                       COALESCE(r."requested_date", r."requested_from"::date) AS requested_date,
                       r."applicant_staff_id", r."applicant_dentist_id",
                       ap."home_clinic_id" AS clinic_id
                FROM "SHIFT_REQUEST" r
                LEFT JOIN "STAFF" ap ON ap."staff_id" = COALESCE(r."applicant_staff_id", r."applicant_dentist_id")
                WHERE r."request_id" = ?
                """, id);
        Long staffId = req.get("applicant_staff_id") == null
                ? (Long) ((Number) req.get("applicant_dentist_id")).longValue()
                : ((Number) req.get("applicant_staff_id")).longValue();
        Long clinicId = req.get("clinic_id") == null ? null
                : ((Number) req.get("clinic_id")).longValue();
        LocalDate day = toLocalDate(req.get("requested_date"));
        return jdbc.queryForList("""
                SELECT st."staff_id", st."name", st."home_clinic_id" AS clinic_id,
                       cl."name" AS clinic_name,
                       CASE WHEN st."home_clinic_id" = ? THEN 'SAME_CLINIC' ELSE 'CROSS_CLINIC' END AS source_type,
                       (SELECT count(*) FROM "SHIFT" c
                         WHERE c."staff_id" = st."staff_id" AND c."source" = 'SUBSTITUTE'
                           AND c."shift_date" >= date_trunc('month', ?::timestamp)::date) AS cover_count_month
                FROM "STAFF" st
                JOIN "CLINIC" cl ON cl."clinic_id" = st."home_clinic_id"
                WHERE st."role_type" = 'DENTIST' AND st."active" = TRUE AND st."staff_id" <> ?
                  AND NOT EXISTS (SELECT 1 FROM "SHIFT" s
                                   WHERE s."staff_id" = st."staff_id" AND s."shift_date" = ?
                                     AND s."status" = 'PUBLISHED')
                ORDER BY CASE WHEN st."home_clinic_id" = ? THEN 0 ELSE 1 END, cover_count_month, st."staff_id"
                """, clinicId, day, staffId, day, clinicId);
    }

    /** 员工绩效总览（管理层复核各医生绩效）。 */
    @GetMapping("/performance")
    public List<Map<String, Object>> performance(@RequestParam(required = false) String month) {
        AccessGuard.requireRole("MANAGER");
        LocalDate start = (month == null || month.isBlank())
                ? LocalDate.now().withDayOfMonth(1)
                : LocalDate.parse(month.matches("\\d{4}-\\d{2}") ? month + "-01" : month).withDayOfMonth(1);
        return jdbc.queryForList("""
                SELECT dp."performance_id", dp."dentist_id", st."name" AS dentist_name,
                       cl."name" AS clinic_name, dp."period_start", dp."period_end",
                       dp."scheduled_days", dp."leave_days", dp."swap_applied_count", dp."cover_shift_count",
                       dp."full_attendance", dp."attendance_bonus", dp."leave_penalty", dp."cover_bonus",
                       dp."final_score", dp."reward_level", dp."review_status"
                FROM "DENTIST_PERFORMANCE" dp
                JOIN "STAFF" st ON st."staff_id" = dp."dentist_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = dp."clinic_id"
                WHERE dp."period_start" = ?
                ORDER BY dp."clinic_id", dp."final_score" DESC NULLS LAST
                """, start);
    }

    /** JDBC 的 date 列在部分驱动下返回 java.sql.Date，这里统一转换，避免直接强转失败。 */
    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    public record ReviewRequest(@NotNull String decision, String setupEligible, String comment) {}
}
