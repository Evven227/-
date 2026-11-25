package com.adc.dental.appointment;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final JdbcTemplate jdbc;

    public AppointmentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 预约列表（按数据范围过滤）：
     * - 患者：只看自己的预约；
     * - 牙医：只看自己班次上的预约；
     * - 诊所员工：只看本诊所的预约；
     * - 管理层：看全部。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        CurrentUser user = AccessGuard.user();
        Long ownPatientId = user.patientId();
        if (ownPatientId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE a.\"patient_id\" = ?" +
                    " ORDER BY a.\"starts_at\", a.\"appointment_id\"", ownPatientId);
        }
        Long ownDentistId = user.hasRole("DENTIST") ? AccessGuard.ownDentistId(jdbc) : null;
        if (ownDentistId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"dentist_id\" = ?" +
                    " ORDER BY a.\"starts_at\", a.\"appointment_id\"", ownDentistId);
        }
        Long clinicId = AccessGuard.ownClinicId();
        if (clinicId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"clinic_id\" = ?" +
                    " ORDER BY a.\"starts_at\", a.\"appointment_id\"", clinicId);
        }
        return jdbc.queryForList(LIST_SQL + " ORDER BY a.\"starts_at\", a.\"appointment_id\"");
    }

    /** 可预约的医生时段：仅 PUBLISHED；诊所员工按诊所过滤，患者/管理员看全部。 */
    @GetMapping("/shifts")
    public List<Map<String, Object>> shifts() {
        CurrentUser user = AccessGuard.user();
        Long clinicId = (user.patientId() == null && !user.isHq()) ? user.clinicId() : null;
        if (clinicId != null) {
            return jdbc.queryForList("""
                SELECT s."shift_id", s."dentist_id", s."clinic_id", s."starts_at", s."ends_at", s."status"
                FROM "SHIFT" s WHERE s."status" = 'PUBLISHED' AND s."clinic_id" = ?
                ORDER BY s."starts_at", s."shift_id"
                """, clinicId);
        }
        return jdbc.queryForList("""
            SELECT s."shift_id", s."dentist_id", s."clinic_id", s."starts_at", s."ends_at", s."status"
            FROM "SHIFT" s WHERE s."status" = 'PUBLISHED'
            ORDER BY s."starts_at", s."shift_id"
            """);
    }

    /**
     * 新建预约。角色：患者 / 前台 / 诊所负责人 / 集团管理员（牙医不直接预约）。
     * 规则：
     * - 预约时间必须完整落在所选班次时间内，且班次为 PUBLISHED；
     * - 患者账号只能为自己预约（patient_id 从登录态锁定）；
     * - 同一班次时间冲突时拒绝；
     * - 操作账号一律取登录态，不信任客户端传入。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateRequest r) {
        CurrentUser user = AccessGuard.requireRole("PATIENT", "RECEPTION", "MANAGER");
        Long patientId = resolvePatientId(r.patientId());
        validateWithinShift(r.shiftId(), r.startsAt(), r.endsAt());
        if (r.endsAt().isBefore(r.startsAt()) || r.endsAt().isEqual(r.startsAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        Integer conflict = jdbc.queryForObject("""
            SELECT count(*) FROM "APPOINTMENT"
            WHERE "shift_id"=? AND "status" NOT IN ('CANCELLED','NO_SHOW')
              AND "starts_at" < ? AND "ends_at" > ?
            """, Integer.class, r.shiftId(), Timestamp.valueOf(r.endsAt()), Timestamp.valueOf(r.startsAt()));
        if (conflict != null && conflict > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该医生时段已有预约");
        return jdbc.queryForMap("""
            INSERT INTO "APPOINTMENT" ("patient_id","shift_id","starts_at","ends_at","status","created_by_account_id","created_at")
            VALUES (?,?,?,?, 'BOOKED',?,current_timestamp) RETURNING "appointment_id","patient_id","shift_id","starts_at","ends_at","status"
            """, patientId, r.shiftId(), Timestamp.valueOf(r.startsAt()), Timestamp.valueOf(r.endsAt()), user.accountId());
    }

    /** 取消预约：写 APPOINTMENT_EVENT 留痕。仅本人预约/本诊所/全局范围可操作。 */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id) {
        CurrentUser user = AccessGuard.requireRole("PATIENT", "DENTIST", "RECEPTION", "MANAGER");
        Map<String, Object> old = loadScopedAppointment(id, user);
        jdbc.update("UPDATE \"APPOINTMENT\" SET \"status\"='CANCELLED' WHERE \"appointment_id\"=? AND \"status\" NOT IN ('CANCELLED','COMPLETED')", id);
        jdbc.update("""
            INSERT INTO "APPOINTMENT_EVENT" ("appointment_id","old_status","new_status","old_shift_id","new_shift_id",
                "old_starts_at","old_ends_at","reason","operator_account_id","occurred_at")
            VALUES (?, ?, 'CANCELLED', ?, ?, ?, ?, 'CANCELLED', ?, current_timestamp)
            """, id, old.get("status"), old.get("shift_id"), old.get("shift_id"),
                old.get("starts_at"), old.get("ends_at"), user.accountId());
        return jdbc.queryForMap("SELECT \"appointment_id\",\"status\" FROM \"APPOINTMENT\" WHERE \"appointment_id\"=?", id);
    }

    /** 改约：新时间同样必须落在目标班次内；写 APPOINTMENT_EVENT。 */
    @PutMapping("/{id}")
    public Map<String, Object> reschedule(@PathVariable long id, @Valid @RequestBody RescheduleRequest r) {
        CurrentUser user = AccessGuard.requireRole("PATIENT", "RECEPTION", "MANAGER");
        Map<String, Object> old = loadScopedAppointment(id, user);
        if ("CANCELLED".equals(old.get("status")) || "COMPLETED".equals(old.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前预约状态不可改约");
        validateWithinShift(r.shiftId(), r.startsAt(), r.endsAt());
        Integer conflict = jdbc.queryForObject("""
            SELECT count(*) FROM "APPOINTMENT" WHERE "shift_id"=? AND "appointment_id"<>?
              AND "status" NOT IN ('CANCELLED','NO_SHOW') AND "starts_at" < ? AND "ends_at" > ?
            """, Integer.class, r.shiftId(), id, Timestamp.valueOf(r.endsAt()), Timestamp.valueOf(r.startsAt()));
        if (conflict != null && conflict > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "新时段已有预约");
        jdbc.update("UPDATE \"APPOINTMENT\" SET \"shift_id\"=?,\"starts_at\"=?,\"ends_at\"=?,\"status\"='BOOKED' WHERE \"appointment_id\"=?",
                r.shiftId(), Timestamp.valueOf(r.startsAt()), Timestamp.valueOf(r.endsAt()), id);
        jdbc.update("""
            INSERT INTO "APPOINTMENT_EVENT" ("appointment_id","old_status","new_status","old_shift_id","new_shift_id","old_starts_at","old_ends_at","new_starts_at","new_ends_at","reason","operator_account_id","occurred_at")
            VALUES (?, ?, 'BOOKED', ?, ?, ?, ?, ?, ?, 'RESCHEDULED', ?, current_timestamp)
            """, id, old.get("status"), old.get("shift_id"), r.shiftId(), old.get("starts_at"), old.get("ends_at"), Timestamp.valueOf(r.startsAt()), Timestamp.valueOf(r.endsAt()), user.accountId());
        return jdbc.queryForMap("SELECT \"appointment_id\",\"shift_id\",\"starts_at\",\"ends_at\",\"status\" FROM \"APPOINTMENT\" WHERE \"appointment_id\"=?", id);
    }

    // ---------- 内部工具 ----------

    private static final String LIST_SQL = """
            SELECT a."appointment_id", a."patient_id", p."name" AS patient_name,
                   a."shift_id", s."dentist_id", s."clinic_id", a."starts_at", a."ends_at", a."status"
            FROM "APPOINTMENT" a JOIN "PATIENT" p ON p."patient_id"=a."patient_id"
            JOIN "SHIFT" s ON s."shift_id"=a."shift_id"
            """;

    /** 患者账号锁定本人；员工账号可为任意患者预约。 */
    private Long resolvePatientId(Long requestPatientId) {
        Long ownPatientId = AccessGuard.ownPatientId();
        if (ownPatientId != null) return ownPatientId;
        if (requestPatientId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择就诊患者");
        return requestPatientId;
    }

    /** 校验班次存在且已发布，且预约区间完整落在班次区间内。 */
    private void validateWithinShift(Long shiftId, LocalDateTime startsAt, LocalDateTime endsAt) {
        List<Map<String, Object>> shifts = jdbc.queryForList("""
            SELECT "starts_at", "ends_at", "status" FROM "SHIFT" WHERE "shift_id" = ?
            """, shiftId);
        if (shifts.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选医生时段不存在");
        Map<String, Object> shift = shifts.getFirst();
        if (!"PUBLISHED".equals(String.valueOf(shift.get("status"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选医生时段尚未发布，不能预约");
        LocalDateTime shiftStart = ((Timestamp) shift.get("starts_at")).toLocalDateTime();
        LocalDateTime shiftEnd = ((Timestamp) shift.get("ends_at")).toLocalDateTime();
        if (startsAt.isBefore(shiftStart) || endsAt.isAfter(shiftEnd))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "预约时间必须位于所选班次 " + shiftStart + " ~ " + shiftEnd + " 之内");
    }

    /** 读取预约并做数据范围校验：患者本人 / 本人班次 / 本诊所 / 全局。 */
    private Map<String, Object> loadScopedAppointment(long id, CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT a."appointment_id", a."patient_id", a."shift_id", a."starts_at", a."ends_at", a."status",
                   s."clinic_id", s."dentist_id"
            FROM "APPOINTMENT" a JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
            WHERE a."appointment_id" = ?
            """, id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在");
        Map<String, Object> row = rows.getFirst();
        if (user.patientId() != null && !user.patientId().equals(((Number) row.get("patient_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本人的预约");
        if (user.patientId() == null && !user.isHq()) {
            Long clinicId = user.clinicId();
            Long ownDentistId = user.hasRole("DENTIST") ? AccessGuard.ownDentistId(jdbc) : null;
            boolean inClinic = clinicId != null && clinicId.equals(((Number) row.get("clinic_id")).longValue());
            boolean ownShift = ownDentistId != null && ownDentistId.equals(((Number) row.get("dentist_id")).longValue());
            if (!inClinic && !ownShift)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本诊所范围内的预约");
        }
        return row;
    }

    public record CreateRequest(Long patientId, @NotNull Long shiftId,
                                @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {}
    public record RescheduleRequest(@NotNull Long shiftId, @NotNull LocalDateTime startsAt,
                                    @NotNull LocalDateTime endsAt) {}
}
