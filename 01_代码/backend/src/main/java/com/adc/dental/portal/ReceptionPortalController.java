package com.adc.dental.portal;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 前台工作台：本店当日预约、到诊签到、就诊提醒、收款二维码、耗材查看。
 */
@RestController
@RequestMapping("/api/portal/reception")
public class ReceptionPortalController {

    private final JdbcTemplate jdbc;

    public ReceptionPortalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 本店当日（或指定日）预约列表，含患者联系方式与到诊状态。 */
    @GetMapping("/appointments")
    public List<Map<String, Object>> appointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long clinicId = clinicScope();
        LocalDate day = date == null ? LocalDate.now() : date;
        return jdbc.queryForList("""
                SELECT a."appointment_id", a."booking_no", a."status", a."starts_at", a."ends_at",
                       a."slot_code", st."slot_name", cl."name" AS clinic_name, a."clinic_id",
                       p."patient_id", p."name" AS patient_name, p."phone", p."special_needs",
                       COALESCE(d."name", '') AS dentist_name,
                       (SELECT count(*) FROM "CHECKIN" c WHERE c."appointment_id" = a."appointment_id") AS checked_in,
                       (SELECT count(*) FROM "ENCOUNTER" e WHERE e."appointment_id" = a."appointment_id") AS encounter_count,
                       (SELECT max(iv."total_amount") FROM "INVOICE" i
                          JOIN "INVOICE_VERSION" iv ON iv."invoice_id" = i."invoice_id"
                          JOIN "ENCOUNTER" e2 ON e2."encounter_id" = i."encounter_id"
                         WHERE e2."appointment_id" = a."appointment_id") AS invoice_total
                FROM "APPOINTMENT" a
                JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
                JOIN "CLINIC" cl ON cl."clinic_id" = a."clinic_id"
                LEFT JOIN "SLOT_TEMPLATE" st ON st."clinic_id" = a."clinic_id" AND st."slot_code" = a."slot_code"
                LEFT JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
                LEFT JOIN "STAFF" d ON d."staff_id" = s."staff_id"
                WHERE a."clinic_id" = ? AND a."starts_at"::date = ?
                  AND a."status" NOT IN ('CANCELLED')
                ORDER BY a."starts_at", a."appointment_id"
                """, clinicId, day);
    }

    /** 到诊签到：写 CHECKIN 留痕（幂等：已签到则跳过）。 */
    @PostMapping("/appointments/{id}/check-in")
    public Map<String, Object> checkIn(@PathVariable long id) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER", "DENTIST");
        Long clinicId = clinicScopeOrNull();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT "appointment_id","patient_id","clinic_id","status"
                FROM "APPOINTMENT" WHERE "appointment_id"=?
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在");
        Map<String, Object> ap = rows.getFirst();
        if (clinicId != null && !clinicId.equals(((Number) ap.get("clinic_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能为本诊所预约签到");
        Integer exists = jdbc.queryForObject(
                "SELECT count(*) FROM \"CHECKIN\" WHERE \"appointment_id\"=?", Integer.class, id);
        if (exists == null || exists == 0) {
            jdbc.update("""
                    INSERT INTO "CHECKIN"("appointment_id","clinic_id","patient_id","arrived_at","taken_by_account_id")
                    VALUES (?,?,?, current_timestamp, ?)
                    """, id, ap.get("clinic_id"), ap.get("patient_id"), user.accountId());
        }
        jdbc.update("UPDATE \"APPOINTMENT\" SET \"status\"='ARRIVED' WHERE \"appointment_id\"=? AND \"status\"='BOOKED'", id);
        return Map.of("appointmentId", id, "checkedIn", true);
    }

    /** 未来 24 小时内待到诊：前台据此电话/站内提醒患者。 */
    @GetMapping("/reminders")
    public List<Map<String, Object>> reminders() {
        Long clinicId = clinicScope();
        return jdbc.queryForList("""
                SELECT a."appointment_id", a."booking_no", a."starts_at", st."slot_name",
                       p."name" AS patient_name, p."phone", a."status",
                       n."status" AS reminder_status, n."scheduled_at" AS reminder_at
                FROM "APPOINTMENT" a
                JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
                LEFT JOIN "SLOT_TEMPLATE" st ON st."clinic_id" = a."clinic_id" AND st."slot_code" = a."slot_code"
                LEFT JOIN "NOTIFICATION" n ON n."ref_type"='APPOINTMENT' AND n."ref_id"=a."appointment_id"
                     AND n."event_type"='APPT_REMINDER_12H'
                WHERE a."clinic_id"=? AND a."status"='BOOKED'
                  AND a."starts_at" BETWEEN current_timestamp AND current_timestamp + INTERVAL '24 hours'
                ORDER BY a."starts_at"
                """, clinicId);
    }

    /**
     * 生成收款二维码：为账单写入 PENDING 支付请求，返回二维码载荷。
     * 载荷格式 ADC-PAY:<requestNo>，前端据此渲染二维码；二维码有效期 15 分钟。
     */
    @PostMapping("/payments/qr")
    public Map<String, Object> createQr(@RequestBody QrRequest r) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        Map<String, Object> inv = jdbc.queryForMap("""
                SELECT iv."invoice_version_id", iv."total_amount", iv."invoice_id", iv."version_no"
                FROM "INVOICE_VERSION" iv WHERE iv."invoice_version_id"=?
                """, r.invoiceVersionId());
        BigDecimal paidSoFar = jdbc.queryForObject("""
                SELECT COALESCE(sum("amount"),0) FROM "PAYMENT"
                 WHERE "invoice_version_id"=? AND "status"='SUCCESS'
                """, BigDecimal.class, r.invoiceVersionId());
        BigDecimal total = (BigDecimal) inv.get("total_amount");
        BigDecimal remain = total.subtract(paidSoFar);
        BigDecimal amount = (r.amount() == null || r.amount().signum() <= 0) ? remain : r.amount();
        if (amount.compareTo(remain) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收款金额超过待付余额 " + remain);
        if (amount.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该账单已结清，无需收款");

        // 同一账单先作废此前未完成的收款请求，避免二维码串单
        jdbc.update("""
                UPDATE "PAYMENT" SET "status"='FAILED', "receipt_no"='SUPERSEDED-' || "payment_id"
                 WHERE "invoice_version_id"=? AND "status"='PENDING'
                """, r.invoiceVersionId());

        String requestNo = "ADC-PAY:" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Map<String, Object> pay = jdbc.queryForMap("""
                INSERT INTO "PAYMENT"("invoice_version_id","request_no","amount","method","status",
                    "paid_at","operator_account_id","qr_code","qr_expires_at","channel")
                VALUES (?,?,?, 'MOBILE_PAYMENT', 'PENDING', current_timestamp, ?,?, current_timestamp + INTERVAL '15 minutes', ?)
                RETURNING "payment_id","request_no","amount","status","qr_expires_at"
                """, r.invoiceVersionId(), requestNo, amount, user.accountId(),
                requestNo, r.channel() == null ? "CLINIC_QR" : r.channel());
        return Map.of(
                "paymentId", pay.get("payment_id"),
                "invoiceId", inv.get("invoice_id"),
                "totalAmount", total,
                "paidAmount", paidSoFar,
                "amount", pay.get("amount"),
                "qrPayload", requestNo,
                "expiresAt", pay.get("qr_expires_at"),
                "status", pay.get("status"));
    }

    /** 查询收款二维码状态（轮询用）。 */
    @GetMapping("/payments/{paymentId}/status")
    public Map<String, Object> qrStatus(@PathVariable long paymentId) {
        AccessGuard.requireRole("RECEPTION", "MANAGER");
        return jdbc.queryForMap("""
                SELECT "payment_id","request_no","amount","status","qr_code","qr_expires_at",
                       ("qr_expires_at" < current_timestamp) AS expired
                FROM "PAYMENT" WHERE "payment_id"=?
                """, paymentId);
    }

    /** 耗材（库存）余量概览：前台按需申领/登记。 */
    @GetMapping("/supplies")
    public List<Map<String, Object>> supplies() {
        Long clinicId = clinicScope();
        return jdbc.queryForList("""
                SELECT m."material_id", m."material_code", m."name", m."unit", m."unit_cost",
                       cs."on_hand" AS quantity, cs."warning_threshold" AS safety_stock,
                       (cs."on_hand" <= cs."warning_threshold") AS low_stock
                FROM "CLINIC_STOCK" cs JOIN "MATERIAL" m ON m."material_id" = cs."material_id"
                WHERE cs."clinic_id" = ? ORDER BY low_stock DESC, m."name"
                """, clinicId);
    }

    private Long clinicScope() {
        Long id = clinicScopeOrNull();
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有绑定诊所");
        return id;
    }

    private Long clinicScopeOrNull() {
        CurrentUser user = AccessGuard.user();
        if (user.isHq()) return null;
        return user.clinicId();
    }

    public record QrRequest(Long invoiceVersionId, BigDecimal amount, String channel) {}
}
