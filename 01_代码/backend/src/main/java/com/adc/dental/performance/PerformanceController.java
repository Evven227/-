package com.adc.dental.performance;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 绩效模块：按周期汇总牙医预约量、就诊量、完成量与治疗项目数，
 * 计算简单绩效得分（出勤率 60% + 完成率 40%），支持负责人复核确认。
 * 只做统计与评级，不做工资、奖金发放。
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    private final JdbcTemplate jdbc;

    public PerformanceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 绩效列表：牙医看自己；诊所负责人看本诊所；集团管理员看全部。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        CurrentUser user = AccessGuard.user();
        if (user.isPatient()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "患者无权查看绩效数据");
        }
        if (user.hasRole("DENTIST") && !user.isHq() && !user.hasRole("MANAGER")) {
            Long ownDentistId = AccessGuard.ownDentistId(jdbc);
            if (ownDentistId == null) return List.of();
            return jdbc.queryForList(LIST_SQL + " WHERE dp.\"dentist_id\" = ?" +
                    " ORDER BY dp.\"period_start\" DESC, dp.\"dentist_id\"", ownDentistId);
        }
        Long clinicId = AccessGuard.ownClinicId();
        if (clinicId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE dp.\"clinic_id\" = ?" +
                    " ORDER BY dp.\"period_start\" DESC, dp.\"dentist_id\"", clinicId);
        }
        return jdbc.queryForList(LIST_SQL + " ORDER BY dp.\"period_start\" DESC, dp.\"dentist_id\"");
    }

    /**
     * 重新计算绩效：按周期汇总 APPOINTMENT / ENCOUNTER / TREATMENT_LINE，
     * 写入 DENTIST_PERFORMANCE（同牙医同周期已存在则更新）。
     * 可指定单个牙医；不指定则为全部执业牙医计算。
     */
    @PostMapping("/recalculate")
    @Transactional
    public List<Map<String, Object>> recalculate(@Valid @RequestBody RecalculateRequest r) {
        AccessGuard.requireRole("MANAGER");
        if (!r.periodEnd().isAfter(r.periodStart()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "周期结束日期必须晚于开始日期");
        int target = r.workloadTargetCount() == null ? 20 : r.workloadTargetCount();

        List<Map<String, Object>> dentists = r.dentistId() != null
                ? jdbc.queryForList("SELECT \"dentist_id\" FROM \"DENTIST\" WHERE \"dentist_id\" = ?", r.dentistId())
                : jdbc.queryForList("SELECT \"dentist_id\" FROM \"DENTIST\" ORDER BY \"dentist_id\"");
        if (r.dentistId() != null && dentists.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "牙医不存在");

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> d : dentists) {
            long dentistId = ((Number) d.get("dentist_id")).longValue();
            Map<String, Object> agg = jdbc.queryForMap("""
                SELECT count(*) AS appointment_count,
                       count(*) FILTER (WHERE a."status" IN ('ARRIVED','COMPLETED')) AS attended_count,
                       count(*) FILTER (WHERE a."status" = 'CANCELLED') AS cancelled_count,
                       count(*) FILTER (WHERE a."status" = 'NO_SHOW') AS no_show_count,
                       count(*) FILTER (WHERE a."status" = 'COMPLETED') AS completed_count,
                       COALESCE((SELECT count(*) FROM "TREATMENT_LINE" t
                                 JOIN "ENCOUNTER" e ON e."encounter_id" = t."encounter_id"
                                 JOIN "APPOINTMENT" a2 ON a2."appointment_id" = e."appointment_id"
                                 JOIN "SHIFT" s2 ON s2."shift_id" = a2."shift_id"
                                 WHERE s2."dentist_id" = ? AND a2."starts_at" >= ? AND a2."starts_at" < ?), 0) AS treatment_count
                FROM "APPOINTMENT" a JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
                WHERE s."dentist_id" = ? AND a."starts_at" >= ? AND a."starts_at" < ?
                """, dentistId, Timestamp(r.periodStart()), Timestamp(r.periodEnd().plusDays(1)),
                    dentistId, Timestamp(r.periodStart()), Timestamp(r.periodEnd().plusDays(1)));

            int appointmentCount = ((Number) agg.get("appointment_count")).intValue();
            int attendedCount = ((Number) agg.get("attended_count")).intValue();
            int cancelledCount = ((Number) agg.get("cancelled_count")).intValue();
            int noShowCount = ((Number) agg.get("no_show_count")).intValue();
            int completedCount = ((Number) agg.get("completed_count")).intValue();
            int treatmentCount = ((Number) agg.get("treatment_count")).intValue();

            BigDecimal score = null;
            String reward = "NONE";
            if (appointmentCount > 0) {
                BigDecimal attendance = BigDecimal.valueOf(attendedCount)
                        .divide(BigDecimal.valueOf(appointmentCount), 4, RoundingMode.HALF_UP);
                BigDecimal completion = BigDecimal.valueOf(completedCount)
                        .divide(BigDecimal.valueOf(appointmentCount), 4, RoundingMode.HALF_UP);
                score = attendance.multiply(BigDecimal.valueOf(60))
                        .add(completion.multiply(BigDecimal.valueOf(40)))
                        .setScale(2, RoundingMode.HALF_UP);
                if (score.compareTo(BigDecimal.valueOf(100)) > 0) score = BigDecimal.valueOf(100);
                if (score.compareTo(BigDecimal.valueOf(90)) >= 0) reward = "EXCELLENT";
                else if (score.compareTo(BigDecimal.valueOf(75)) >= 0) reward = "GOOD";
                else if (score.compareTo(BigDecimal.valueOf(60)) >= 0) reward = "NORMAL";
                else reward = "NONE";
            }

            Long clinicId = primaryClinic(dentistId);
            Integer existing = jdbc.queryForObject("""
                SELECT count(*) FROM "DENTIST_PERFORMANCE"
                WHERE "dentist_id" = ? AND "period_start" = ? AND "period_end" = ?
                """, Integer.class, dentistId, Date(r.periodStart()), Date(r.periodEnd()));
            if (existing != null && existing > 0) {
                jdbc.update("""
                    UPDATE "DENTIST_PERFORMANCE" SET
                        "clinic_id" = ?, "appointment_count" = ?, "attended_count" = ?, "cancelled_count" = ?,
                        "no_show_count" = ?, "completed_count" = ?, "treatment_count" = ?, "workload_target_count" = ?,
                        "performance_score" = ?, "reward_level" = ?, "review_status" = 'DRAFT',
                        "calculation_version" = 'v1', "calculated_at" = current_timestamp
                    WHERE "dentist_id" = ? AND "period_start" = ? AND "period_end" = ?
                    """, clinicId, appointmentCount, attendedCount, cancelledCount, noShowCount, completedCount,
                        treatmentCount, target, score, reward, dentistId, Date(r.periodStart()), Date(r.periodEnd()));
            } else {
                jdbc.update("""
                    INSERT INTO "DENTIST_PERFORMANCE" ("dentist_id","clinic_id","period_start","period_end",
                        "appointment_count","attended_count","cancelled_count","no_show_count","completed_count",
                        "treatment_count","workload_target_count","performance_score","reward_level","review_status",
                        "calculation_version","calculated_at","created_at")
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'DRAFT', 'v1', current_timestamp, current_timestamp)
                    """, dentistId, clinicId, Date(r.periodStart()), Date(r.periodEnd()), appointmentCount, attendedCount,
                        cancelledCount, noShowCount, completedCount, treatmentCount, target, score, reward);
            }
            result.add(Map.of("dentistId", dentistId, "appointmentCount", appointmentCount,
                    "attendedCount", attendedCount, "completedCount", completedCount,
                    "treatmentCount", treatmentCount, "performanceScore", score == null ? "" : score,
                    "rewardLevel", reward));
        }
        return result;
    }

    /** 复核绩效：DRAFT -> CONFIRMED / ARCHIVED。 */
    @PutMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable long id, @Valid @RequestBody ReviewRequest r) {
        CurrentUser user = AccessGuard.requireRole("MANAGER");
        if (user.staffId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "复核人不是员工账号");
        String action = r.action().toUpperCase();
        String target;
        if ("CONFIRM".equals(action)) target = "CONFIRMED";
        else if ("ARCHIVE".equals(action)) target = "ARCHIVED";
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的操作：" + action + "（可选 CONFIRM / ARCHIVE）");
        int n = jdbc.update("""
            UPDATE "DENTIST_PERFORMANCE" SET "review_status" = ?, "reviewed_by_account_id" = ?, "reviewed_at" = current_timestamp
            WHERE "performance_id" = ?
            """, target, user.accountId(), id);
        if (n == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绩效记录不存在");
        return jdbc.queryForMap("""
            SELECT "performance_id","dentist_id","period_start","period_end","performance_score","reward_level","review_status"
            FROM "DENTIST_PERFORMANCE" WHERE "performance_id" = ?
            """, id);
    }

    // ---------- 内部工具 ----------

    private static final String LIST_SQL = """
            SELECT dp."performance_id", dp."dentist_id", st."name" AS dentist_name, dp."clinic_id",
                   cl."name" AS clinic_name, dp."period_start", dp."period_end",
                   dp."appointment_count", dp."attended_count", dp."cancelled_count", dp."no_show_count",
                   dp."completed_count", dp."treatment_count", dp."workload_target_count",
                   dp."performance_score", dp."reward_level", dp."review_status",
                   dp."reviewed_by_account_id", dp."reviewed_at", dp."calculated_at"
            FROM "DENTIST_PERFORMANCE" dp
            JOIN "STAFF" st ON st."staff_id" = dp."dentist_id"
            LEFT JOIN "CLINIC" cl ON cl."clinic_id" = dp."clinic_id"
            """;

    /** 牙医的主执业诊所：取状态为 ACTIVE 且生效中的执业点，多个取第一个。 */
    private Long primaryClinic(long dentistId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT "clinic_id" FROM "PRACTICE"
            WHERE "dentist_id" = ? AND "status" = 'ACTIVE'
              AND "valid_from" <= CURRENT_DATE AND ("valid_to" IS NULL OR "valid_to" >= CURRENT_DATE)
            ORDER BY "practice_id" LIMIT 1
            """, dentistId);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("clinic_id")).longValue();
    }

    private static java.sql.Date Date(LocalDate d) {
        return java.sql.Date.valueOf(d);
    }

    private static java.sql.Timestamp Timestamp(LocalDate d) {
        return java.sql.Timestamp.valueOf(d.atStartOfDay());
    }

    public record RecalculateRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                                     Long dentistId, Integer workloadTargetCount) {}

    public record ReviewRequest(@NotNull String action) {}
}
