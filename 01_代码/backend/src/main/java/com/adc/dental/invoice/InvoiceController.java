package com.adc.dental.invoice;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 结算模块：按就诊的治疗项目生成账单（价格快照），支持确认、收款、作废。
 * 只做到“账单 + 收款记录”，不做工资、利润、退款、保险理赔。
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final JdbcTemplate jdbc;

    public InvoiceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 账单列表（按数据范围过滤）：患者本人 / 班次牙医 / 本诊所 / 全局。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        CurrentUser user = AccessGuard.user();
        Long ownPatientId = user.patientId();
        if (ownPatientId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE a.\"patient_id\" = ?" +
                    " ORDER BY i.\"invoice_id\" DESC", ownPatientId);
        }
        Long ownDentistId = user.hasRole("DENTIST") ? AccessGuard.ownDentistId(jdbc) : null;
        if (ownDentistId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"dentist_id\" = ?" +
                    " ORDER BY i.\"invoice_id\" DESC", ownDentistId);
        }
        Long clinicId = AccessGuard.ownClinicId();
        if (clinicId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"clinic_id\" = ?" +
                    " ORDER BY i.\"invoice_id\" DESC", clinicId);
        }
        return jdbc.queryForList(LIST_SQL + " ORDER BY i.\"invoice_id\" DESC");
    }

    /** 账单详情：含当前版本、明细行、收款记录。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        AccessGuard.user();
        List<Map<String, Object>> rows = jdbc.queryForList(LIST_SQL + " WHERE i.\"invoice_id\" = ?", id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        List<Map<String, Object>> versions = jdbc.queryForList("""
            SELECT "invoice_version_id","version_no","total_amount","currency","issued_at",
                   "issued_by_account_id","correction_reason"
            FROM "INVOICE_VERSION" WHERE "invoice_id" = ? ORDER BY "version_no"
            """, id);
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT l."invoice_line_id", l."description_snapshot", l."unit_snapshot", l."quantity_snapshot",
                   l."unit_price_snapshot", l."line_total", v."version_no"
            FROM "INVOICE_LINE" l JOIN "INVOICE_VERSION" v ON v."invoice_version_id" = l."invoice_version_id"
            WHERE v."invoice_id" = ? ORDER BY v."version_no", l."invoice_line_id"
            """, id);
        List<Map<String, Object>> payments = jdbc.queryForList("""
            SELECT pm."payment_id", pm."request_no", pm."receipt_no", pm."amount", pm."method", pm."status",
                   pm."paid_at", pm."invoice_version_id", v."version_no"
            FROM "PAYMENT" pm JOIN "INVOICE_VERSION" v ON v."invoice_version_id" = pm."invoice_version_id"
            WHERE v."invoice_id" = ? AND pm."status" = 'SUCCESS'
            ORDER BY pm."paid_at", pm."payment_id"
            """, id);
        Map<String, Object> result = new java.util.LinkedHashMap<>(rows.getFirst());
        result.put("versions", versions);
        result.put("lines", lines);
        result.put("payments", payments);
        return result;
    }

    /**
     * 按就诊生成账单：快照治疗项目与当前价目，生成 DRAFT 账单与 v1 版本。
     * 要求就诊存在、至少一条治疗项目、尚未生成过账单。
     */
    @PostMapping("/encounters/{encounterId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> generate(@PathVariable long encounterId) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        List<Map<String, Object>> encounters = jdbc.queryForList("""
            SELECT e."encounter_id", e."status", a."patient_id", s."clinic_id"
            FROM "ENCOUNTER" e
            JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
            JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
            WHERE e."encounter_id" = ?
            """, encounterId);
        if (encounters.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "就诊记录不存在");
        Map<String, Object> encounter = encounters.getFirst();
        if ("VOID".equals(String.valueOf(encounter.get("status"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已作废的就诊不能生成账单");
        if (user.clinicId() != null && !user.isHq()
                && !user.clinicId().equals(((Number) encounter.get("clinic_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能为本诊所的就诊生成账单");
        if (jdbc.queryForObject("SELECT count(*) FROM \"INVOICE\" WHERE \"encounter_id\"=?", Integer.class, encounterId) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该就诊已生成账单");
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT t."treatment_line_id", t."name_snapshot", t."unit_snapshot", t."quantity", t."unit_price_snapshot"
            FROM "TREATMENT_LINE" t WHERE t."encounter_id" = ? ORDER BY t."treatment_line_id"
            """, encounterId);
        if (lines.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该就诊还没有治疗项目，不能生成账单");

        String invoiceNo = jdbc.queryForObject("""
            SELECT 'INV-' || to_char(CURRENT_DATE, 'YYYYMMDD') || '-' ||
                   lpad((count(*) FILTER (WHERE "invoice_no" LIKE 'INV-' || to_char(CURRENT_DATE, 'YYYYMMDD') || '%') + 1)::text, 4, '0')
            FROM "INVOICE"
            """, String.class);
        Long invoiceId = jdbc.queryForObject("""
            INSERT INTO "INVOICE" ("encounter_id","invoice_no","status")
            VALUES (?, ?, 'DRAFT') RETURNING "invoice_id"
            """, Long.class, encounterId, invoiceNo);
        Long versionId = jdbc.queryForObject("""
            INSERT INTO "INVOICE_VERSION" ("invoice_id","version_no","total_amount","currency","issued_at","issued_by_account_id")
            VALUES (?, 1, 0, 'CNY', current_timestamp, ?) RETURNING "invoice_version_id"
            """, Long.class, invoiceId, user.accountId());
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            BigDecimal quantity = (BigDecimal) line.get("quantity");
            BigDecimal unitPrice = (BigDecimal) line.get("unit_price_snapshot");
            total = total.add(quantity.multiply(unitPrice));
            jdbc.update("""
                INSERT INTO "INVOICE_LINE" ("invoice_version_id","treatment_line_id","description_snapshot",
                    "unit_snapshot","quantity_snapshot","unit_price_snapshot","line_total")
                VALUES (?,?,?,?,?,?,?)
                """, versionId, line.get("treatment_line_id"), line.get("name_snapshot"),
                    line.get("unit_snapshot"), quantity, unitPrice, quantity.multiply(unitPrice));
        }
        jdbc.update("UPDATE \"INVOICE_VERSION\" SET \"total_amount\"=? WHERE \"invoice_version_id\"=?", total, versionId);
        return jdbc.queryForMap("""
            SELECT i."invoice_id", i."invoice_no", i."status", v."total_amount", v."version_no"
            FROM "INVOICE" i JOIN "INVOICE_VERSION" v ON v."invoice_id" = i."invoice_id"
            WHERE i."invoice_id" = ? AND v."version_no" = 1
            """, invoiceId);
    }

    /** 确认账单：DRAFT -> CONFIRMED。 */
    @PutMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id) {
        AccessGuard.requireRole("RECEPTION", "MANAGER");
        String status = currentStatus(id);
        if (!"DRAFT".equals(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿账单可以确认");
        jdbc.update("UPDATE \"INVOICE\" SET \"status\"='CONFIRMED' WHERE \"invoice_id\"=?", id);
        return jdbc.queryForMap("SELECT \"invoice_id\",\"invoice_no\",\"status\" FROM \"INVOICE\" WHERE \"invoice_id\"=?", id);
    }

    /**
     * 收款：对已确认账单登记一笔收款（CASH / BANK_CARD / MOBILE_PAYMENT），
     * 累计收款达到账单金额后账单自动变为 PAID。
     */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> pay(@PathVariable long id, @Valid @RequestBody PaymentRequest r) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        String status = currentStatus(id);
        if (!"CONFIRMED".equals(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已确认的账单可以收款");
        Map<String, Object> version = jdbc.queryForMap("""
            SELECT "invoice_version_id","total_amount" FROM "INVOICE_VERSION"
            WHERE "invoice_id" = ? ORDER BY "version_no" DESC LIMIT 1
            """, id);
        BigDecimal paidSoFar = jdbc.queryForObject("""
            SELECT COALESCE(SUM(pm."amount"), 0) FROM "PAYMENT" pm
            WHERE pm."invoice_version_id" = ? AND pm."status" = 'SUCCESS'
            """, BigDecimal.class, version.get("invoice_version_id"));
        BigDecimal total = (BigDecimal) version.get("total_amount");
        BigDecimal after = paidSoFar.add(r.amount());
        if (after.compareTo(total) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "收款超额：应收 " + total + "，已收 " + paidSoFar);

        String requestNo = "PAY-" + System.currentTimeMillis();
        String receiptNo = "RCP-" + String.format("%08d", Math.abs(requestNo.hashCode() % 100000000));
        Long paymentId = jdbc.queryForObject("""
            INSERT INTO "PAYMENT" ("invoice_version_id","request_no","receipt_no","amount","method","status","paid_at","operator_account_id")
            VALUES (?,?,?,?,?, 'SUCCESS', current_timestamp, ?) RETURNING "payment_id"
            """, Long.class, version.get("invoice_version_id"), requestNo, receiptNo, r.amount(), r.method(), user.accountId());
        if (after.compareTo(total) == 0) {
            jdbc.update("UPDATE \"INVOICE\" SET \"status\"='PAID' WHERE \"invoice_id\"=?", id);
        }
        return jdbc.queryForMap("""
            SELECT pm."payment_id", pm."request_no", pm."receipt_no", pm."amount", pm."method", pm."status", pm."paid_at",
                   i."status" AS invoice_status
            FROM "PAYMENT" pm JOIN "INVOICE_VERSION" v ON v."invoice_version_id" = pm."invoice_version_id"
            JOIN "INVOICE" i ON i."invoice_id" = v."invoice_id"
            WHERE pm."payment_id" = ?
            """, paymentId);
    }

    /** 作废账单：DRAFT / CONFIRMED -> VOID。 */
    @PutMapping("/{id}/void")
    public Map<String, Object> voidInvoice(@PathVariable long id) {
        AccessGuard.requireRole("MANAGER");
        String status = currentStatus(id);
        if ("PAID".equals(status) || "VOID".equals(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可作废：" + status);
        jdbc.update("UPDATE \"INVOICE\" SET \"status\"='VOID' WHERE \"invoice_id\"=?", id);
        return jdbc.queryForMap("SELECT \"invoice_id\",\"invoice_no\",\"status\" FROM \"INVOICE\" WHERE \"invoice_id\"=?", id);
    }

    // ---------- 内部工具 ----------

    private static final String LIST_SQL = """
            SELECT i."invoice_id", i."invoice_no", i."status", e."encounter_id",
                   a."patient_id", p."name" AS patient_name, s."dentist_id", st."name" AS dentist_name, s."clinic_id",
                   (SELECT v."total_amount" FROM "INVOICE_VERSION" v WHERE v."invoice_id" = i."invoice_id"
                      ORDER BY v."version_no" DESC LIMIT 1) AS total_amount,
                   (SELECT COALESCE(SUM(pm."amount"), 0) FROM "PAYMENT" pm
                      JOIN "INVOICE_VERSION" v2 ON v2."invoice_version_id" = pm."invoice_version_id"
                      WHERE v2."invoice_id" = i."invoice_id" AND pm."status" = 'SUCCESS') AS paid_amount
            FROM "INVOICE" i
            JOIN "ENCOUNTER" e ON e."encounter_id" = i."encounter_id"
            JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
            JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
            JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
            JOIN "STAFF" st ON st."staff_id" = s."dentist_id"
            """;

    private String currentStatus(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"status\" FROM \"INVOICE\" WHERE \"invoice_id\" = ?", id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        return String.valueOf(rows.getFirst().get("status"));
    }

    public record PaymentRequest(@NotNull @Positive BigDecimal amount, @NotBlank String method) {}
}
