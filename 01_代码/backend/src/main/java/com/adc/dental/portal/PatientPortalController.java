package com.adc.dental.portal;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 患者自助门户。
 *
 * 预约口径：号源为「诊所 × 日期 × 时段」，每个时段容量 3（见 BOOKING_SLOT.capacity），
 * 由数据库触发器 fn_slot_capacity_guard 硬拦截；同一患者不可跨店同时段（EXCLUDE 约束），
 * 故本层只做前置校验与友好报错，真正的容量一致性由数据库保证。
 */
@RestController
@RequestMapping("/api/portal/patient")
public class PatientPortalController {

    private final JdbcTemplate jdbc;

    public PatientPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 未来 N 天可预约时段（两店全量，含余量与当日值班医生）。 */
    @GetMapping("/slots")
    public List<Map<String, Object>> slots(@RequestParam(defaultValue = "7") int days) {
        AccessGuard.requireRole("PATIENT");
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(Math.max(0, days - 1));
        return jdbc.queryForList("""
                SELECT "clinic_id", "clinic_name", "slot_date", "slot_code", "slot_name",
                       "period_code", "start_time", "end_time", "capacity", "booked",
                       "remaining", "full_flag", "duty_dentists"
                FROM "V_SLOT_AVAILABILITY"
                WHERE "slot_date" BETWEEN ? AND ?
                  AND ("slot_date" + "start_time") > current_timestamp
                ORDER BY "slot_date", "clinic_id", "start_time"
                """, from, to);
    }

    /** 我的预约列表。 */
    @GetMapping("/appointments")
    public List<Map<String, Object>> appointments() {
        long patientId = AccessGuard.requireOwnPatientId();
        return jdbc.queryForList("""
                SELECT a."appointment_id", a."booking_no", a."status", a."starts_at", a."ends_at",
                       a."clinic_id", cl."name" AS clinic_name, a."slot_code", st."slot_name",
                       st."period_code", COALESCE(d."name", '') AS dentist_name,
                       n."status" AS reminder_status, n."scheduled_at" AS reminder_at
                FROM "APPOINTMENT" a
                JOIN "CLINIC" cl ON cl."clinic_id" = a."clinic_id"
                LEFT JOIN "SLOT_TEMPLATE" st ON st."clinic_id" = a."clinic_id" AND st."slot_code" = a."slot_code"
                LEFT JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
                LEFT JOIN "STAFF" d ON d."staff_id" = s."staff_id"
                LEFT JOIN LATERAL (
                        SELECT rn."status", rn."scheduled_at" FROM "NOTIFICATION" rn
                         WHERE rn."ref_type" = 'APPOINTMENT' AND rn."ref_id" = a."appointment_id"
                           AND rn."event_type" = 'APPT_REMINDER_12H'
                         ORDER BY rn."notification_id" DESC LIMIT 1) n ON TRUE
                WHERE a."patient_id" = ?
                ORDER BY a."starts_at" DESC, a."appointment_id" DESC
                """, patientId);
    }

    /** 预约下单：按 诊所+日期+时段，名额由数据库保证。成功后写入 12 小时提醒。 */
    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> book(@RequestBody BookRequest r) {
        CurrentUser user = AccessGuard.requireRole("PATIENT");
        long patientId = AccessGuard.requireOwnPatientId();
        Map<String, Object> slot = loadSlot(r.clinicId(), r.slotDate(), r.slotCode());
        if (slot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "所选时段不存在或未开放");

        Long shiftId = pickLeastBusyShift(r.clinicId(), r.slotDate());
        if (shiftId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该诊所当天没有值班医生，无法预约");

        LocalDateTime startsAt = LocalDateTime.of(r.slotDate(), toLocalTime(slot.get("start_time")));
        LocalDateTime endsAt = LocalDateTime.of(r.slotDate(), toLocalTime(slot.get("end_time")));
        if (startsAt.isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能预约已经过去的时段");

        String bookingNo = "BK" + String.valueOf(System.currentTimeMillis()).substring(5)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        try {
            Map<String, Object> created = jdbc.queryForMap("""
                    INSERT INTO "APPOINTMENT"
                      ("patient_id","shift_id","starts_at","ends_at","status","created_by_account_id",
                       "created_at","clinic_id","slot_code","booking_no")
                    VALUES (?,?,?,?, 'BOOKED',?, current_timestamp, ?,?,?)
                    RETURNING "appointment_id","booking_no","clinic_id","slot_code","starts_at","ends_at","status"
                    """, patientId, shiftId, Timestamp.valueOf(startsAt), Timestamp.valueOf(endsAt),
                    user.accountId(), r.clinicId(), r.slotCode(), bookingNo);
            scheduleReminder(patientId, ((Number) created.get("appointment_id")).longValue(), startsAt);
            return created;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw translateIntegrityError(e);
        }
    }

    /**
     * 改约：取消原号并占用新号；提醒随之顺延。原名额立即释放（触发器实时统计）。
     * 医生分配沿用「原医生当天仍在目标诊所值班则保留原医生，否则挑当天最闲的」，
     * 避免患者改约后预约悄悄转给同店其他医生、原医生看不到该患者。
     */
    @PostMapping("/appointments/{id}/reschedule")
    public Map<String, Object> reschedule(@PathVariable long id, @RequestBody BookRequest r) {
        AccessGuard.requireRole("PATIENT");
        long patientId = AccessGuard.requireOwnPatientId();
        Map<String, Object> old = loadOwnAppointment(id, patientId);
        if ("CANCELLED".equals(old.get("status")) || "COMPLETED".equals(old.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前预约状态不可改约");

        Map<String, Object> slot = loadSlot(r.clinicId(), r.slotDate(), r.slotCode());
        if (slot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目标时段不存在或未开放");
        Long shiftId = pickShiftForReschedule(old, r.clinicId(), r.slotDate());
        if (shiftId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该诊所当天没有值班医生，无法改约");

        LocalDateTime startsAt = LocalDateTime.of(r.slotDate(), toLocalTime(slot.get("start_time")));
        LocalDateTime endsAt = LocalDateTime.of(r.slotDate(), toLocalTime(slot.get("end_time")));
        if (startsAt.isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能改约到已经过去的时段");

        try {
            jdbc.update("""
                    UPDATE "APPOINTMENT"
                       SET "shift_id"=?, "clinic_id"=?, "slot_code"=?, "starts_at"=?, "ends_at"=?,
                           "status"='BOOKED', "rescheduled_from"=?
                     WHERE "appointment_id"=?
                    """, shiftId, r.clinicId(), r.slotCode(), Timestamp.valueOf(startsAt),
                    Timestamp.valueOf(endsAt), id, id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw translateIntegrityError(e);
        }
        cancelReminder(id);
        scheduleReminder(patientId, id, startsAt);
        return jdbc.queryForMap("""
                SELECT "appointment_id","booking_no","clinic_id","slot_code","starts_at","ends_at","status"
                FROM "APPOINTMENT" WHERE "appointment_id"=?
                """, id);
    }

    /** 取消：名额立即释放，待发提醒作废。 */
    @PostMapping("/appointments/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id) {
        AccessGuard.requireRole("PATIENT");
        long patientId = AccessGuard.requireOwnPatientId();
        Map<String, Object> old = loadOwnAppointment(id, patientId);
        if ("CANCELLED".equals(old.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该预约已取消");
        jdbc.update("""
                UPDATE "APPOINTMENT" SET "status"='CANCELLED', "cancelled_at"=current_timestamp
                 WHERE "appointment_id"=?
                """, id);
        cancelReminder(id);
        return Map.of("appointmentId", id, "status", "CANCELLED");
    }

    /** 个人资料（档案 + 过敏原）。 */
    @GetMapping("/profile")
    public Map<String, Object> profile() {
        long patientId = AccessGuard.requireOwnPatientId();
        Map<String, Object> profile = jdbc.queryForMap("""
                SELECT "patient_id","master_no","name","birth_date","address","phone","email",
                       "emergency_name","emergency_phone","special_needs","allergy_assessment"
                FROM "PATIENT" WHERE "patient_id"=?
                """, patientId);
        List<Map<String, Object>> allergies = jdbc.queryForList("""
                SELECT al."name" AS allergen, pa."reaction", pa."notes", pa."recorded_at"
                FROM "PATIENT_ALLERGY" pa JOIN "ALLERGEN" al ON al."allergen_id" = pa."allergen_id"
                WHERE pa."patient_id" = ? ORDER BY al."name"
                """, patientId);
        return Map.of("profile", profile, "allergies", allergies);
    }

    /** 历史治疗记录（就诊 + 治疗项目明细）。 */
    @GetMapping("/history")
    public List<Map<String, Object>> history() {
        long patientId = AccessGuard.requireOwnPatientId();
        return jdbc.queryForList("""
                SELECT e."encounter_id", e."status", e."arrived_at", e."chief_complaint", e."diagnosis",
                       COALESCE(d."name", '') AS dentist_name, a."clinic_id", cl."name" AS clinic_name,
                       tl."treatment_line_id", tl."name_snapshot", tl."quantity", tl."unit_price_snapshot",
                       tl."notes" AS line_notes, tl."confirmed_at"
                FROM "ENCOUNTER" e
                JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = a."clinic_id"
                LEFT JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
                LEFT JOIN "STAFF" d ON d."staff_id" = s."staff_id"
                LEFT JOIN "TREATMENT_LINE" tl ON tl."encounter_id" = e."encounter_id"
                WHERE a."patient_id" = ?
                ORDER BY e."arrived_at" DESC, tl."treatment_line_id"
                """, patientId);
    }

    /** 我的账单（含已付/待付）。 */
    @GetMapping("/invoices")
    public List<Map<String, Object>> invoices() {
        long patientId = AccessGuard.requireOwnPatientId();
        return jdbc.queryForList("""
                SELECT i."invoice_id", i."invoice_no", i."status" AS invoice_status,
                       iv."version_no", iv."total_amount", iv."issued_at",
                       COALESCE(paid."amount", 0) AS paid_amount,
                       (iv."total_amount" - COALESCE(paid."amount", 0)) AS unpaid_amount,
                       cl."name" AS clinic_name
                FROM "INVOICE" i
                JOIN "INVOICE_VERSION" iv ON iv."invoice_id" = i."invoice_id"
                JOIN "ENCOUNTER" e ON e."encounter_id" = i."encounter_id"
                JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = a."clinic_id"
                LEFT JOIN (SELECT "invoice_version_id", sum("amount") AS amount FROM "PAYMENT"
                            WHERE "status"='SUCCESS' GROUP BY "invoice_version_id") paid
                       ON paid."invoice_version_id" = iv."invoice_version_id"
                WHERE a."patient_id" = ?
                ORDER BY iv."issued_at" DESC
                """, patientId);
    }

    // ---------- 内部方法 ----------

    /** JDBC 的 time 列返回 java.sql.Time，版本差异下也可能是 LocalTime，这里统一转换。 */
    private LocalTime toLocalTime(Object value) {
        if (value instanceof java.sql.Time t) return t.toLocalTime();
        if (value instanceof LocalTime lt) return lt;
        if (value instanceof java.util.Date d) return new java.sql.Time(d.getTime()).toLocalTime();
        return LocalTime.parse(String.valueOf(value));
    }

    private Map<String, Object> loadSlot(Long clinicId, LocalDate date, String slotCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT "start_time", "end_time", "capacity"
                FROM "SLOT_TEMPLATE"
                WHERE "clinic_id"=? AND "slot_code"=? AND "active"=TRUE
                """, clinicId, slotCode);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 在当日值班医生中挑当前预约最少的一位，实现诊所层面的负载均衡。 */
    /**
     * 改约选班：原接诊医生当天仍在目标诊所值班 → 保留原医生（患者不用换医生）；
     * 否则（原医生当天休息 / 已调到别店）才退回「当天最闲的医生」。
     */
    private Long pickShiftForReschedule(Map<String, Object> oldAppointment, Long clinicId, LocalDate date) {
        Long oldShiftId = oldAppointment.get("shift_id") == null
                ? null : ((Number) oldAppointment.get("shift_id")).longValue();
        if (oldShiftId != null) {
            List<Map<String, Object>> same = jdbc.queryForList("""
                    SELECT s."shift_id" FROM "SHIFT" s
                    WHERE s."clinic_id"=? AND s."shift_date"=? AND s."status"='PUBLISHED'
                      AND s."role_type"='DENTIST'
                      AND s."staff_id" = (SELECT st."staff_id" FROM "SHIFT" st WHERE st."shift_id"=?)
                    ORDER BY s."shift_id"
                    """, clinicId, date, oldShiftId);
            if (!same.isEmpty())
                return ((Number) same.getFirst().get("shift_id")).longValue();
        }
        return pickLeastBusyShift(clinicId, date);
    }

    private Long pickLeastBusyShift(Long clinicId, LocalDate date) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s."shift_id", count(a."appointment_id") AS cnt
                FROM "SHIFT" s
                LEFT JOIN "APPOINTMENT" a ON a."shift_id" = s."shift_id"
                     AND a."status" NOT IN ('CANCELLED','NO_SHOW')
                WHERE s."clinic_id"=? AND s."shift_date"=? AND s."role_type"='DENTIST'
                  AND s."status"='PUBLISHED'
                GROUP BY s."shift_id"
                ORDER BY cnt, s."shift_id"
                """, clinicId, date);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("shift_id")).longValue();
    }

    private Map<String, Object> loadOwnAppointment(long id, long patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT "appointment_id","status","shift_id","clinic_id" FROM "APPOINTMENT"
                 WHERE "appointment_id"=? AND "patient_id"=?
                """, id, patientId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在");
        return rows.getFirst();
    }

    /** 写入就诊前 12 小时提醒（尚未到点则 PENDING，由定时任务扫描发送）。 */
    private void scheduleReminder(long patientId, long appointmentId, LocalDateTime startsAt) {
        LocalDateTime remindAt = startsAt.minusHours(12);
        jdbc.update("""
                INSERT INTO "NOTIFICATION"
                  ("account_id","patient_id","channel","event_type","title","body",
                   "ref_type","ref_id","status","scheduled_at","created_at")
                VALUES (NULL,?, 'INAPP', 'APPT_REMINDER_12H', ?, ?,
                        'APPOINTMENT',?,?, ?, current_timestamp)
                """, patientId,
                "就诊提醒", "您在 " + startsAt.toLocalDate() + " " + startsAt.toLocalTime()
                        + " 有一个预约，请提前到达诊所。",
                appointmentId,
                remindAt.isBefore(LocalDateTime.now()) ? "SENT" : "PENDING",
                Timestamp.valueOf(remindAt));
    }

    private void cancelReminder(long appointmentId) {
        jdbc.update("""
                UPDATE "NOTIFICATION" SET "status"='CANCELLED'
                 WHERE "ref_type"='APPOINTMENT' AND "ref_id"=? AND "event_type"='APPT_REMINDER_12H'
                   AND "status"='PENDING'
                """, appointmentId);
    }

    /** 把数据库层硬约束翻译成患者能看懂的提示。 */
    private ResponseStatusException translateIntegrityError(
            org.springframework.dao.DataIntegrityViolationException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        if (msg.contains("该时段已约满")) return new ResponseStatusException(HttpStatus.CONFLICT, "该时段已约满，请选择其他时段或其他门店");
        if (msg.contains("ex_appt_patient_overlap") || msg.contains("conflicts with existing key"))
            return new ResponseStatusException(HttpStatus.CONFLICT, "您在该时间段已有预约，不能重复预约");
        return new ResponseStatusException(HttpStatus.CONFLICT, "预约失败：" + msg);
    }

    public record BookRequest(@NotNull Long clinicId, @NotNull LocalDate slotDate,
                              @NotBlank String slotCode) {}
}
