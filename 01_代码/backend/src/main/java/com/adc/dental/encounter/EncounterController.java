package com.adc.dental.encounter;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.Valid;
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
 * 就诊模块：预约签到生成就诊记录，牙医开始/完成就诊并登记治疗项目。
 * 结算、库存、绩效分别由 Invoice / Inventory / Performance 控制器负责。
 */
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {
    private final JdbcTemplate jdbc;

    public EncounterController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 就诊列表（按数据范围过滤）：
     * - 患者：本人就诊；- 牙医：本人班次上的就诊；
     * - 诊所员工：本诊所就诊；- 集团管理员：全部。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        CurrentUser user = AccessGuard.user();
        Long ownPatientId = user.patientId();
        if (ownPatientId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE a.\"patient_id\" = ?" +
                    " ORDER BY e.\"arrived_at\" DESC, e.\"encounter_id\" DESC", ownPatientId);
        }
        Long ownDentistId = user.hasRole("DENTIST") ? AccessGuard.ownDentistId(jdbc) : null;
        if (ownDentistId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"dentist_id\" = ?" +
                    " ORDER BY e.\"arrived_at\" DESC, e.\"encounter_id\" DESC", ownDentistId);
        }
        Long clinicId = AccessGuard.ownClinicId();
        if (clinicId != null) {
            return jdbc.queryForList(LIST_SQL + " WHERE s.\"clinic_id\" = ?" +
                    " ORDER BY e.\"arrived_at\" DESC, e.\"encounter_id\" DESC", clinicId);
        }
        return jdbc.queryForList(LIST_SQL + " ORDER BY e.\"arrived_at\" DESC, e.\"encounter_id\" DESC");
    }

    /** 就诊详情：含治疗项目行。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        CurrentUser user = AccessGuard.user();
        Map<String, Object> encounter = loadScoped(id, user);
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT t."treatment_line_id", t."treatment_item_id", ti."item_code", t."name_snapshot",
                   t."quantity", t."unit_snapshot", t."unit_price_snapshot",
                   (t."quantity" * t."unit_price_snapshot") AS line_total, t."notes", t."confirmed_at"
            FROM "TREATMENT_LINE" t JOIN "TREATMENT_ITEM" ti ON ti."treatment_item_id" = t."treatment_item_id"
            WHERE t."encounter_id" = ? ORDER BY t."treatment_line_id"
            """, id);
        Map<String, Object> result = new java.util.LinkedHashMap<>(encounter);
        result.put("treatmentLines", lines);
        return result;
    }

    /**
     * 预约签到：前台/诊所负责人/集团管理员对已发布预约办理签到，
     * 生成 ENCOUNTER(ARRIVED)，预约状态同步变为 ARRIVED 并写事件留痕。
     */
    @PostMapping("/appointments/{appointmentId}/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> checkIn(@PathVariable long appointmentId) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        List<Map<String, Object>> appts = jdbc.queryForList("""
            SELECT a."appointment_id", a."patient_id", a."status", s."clinic_id"
            FROM "APPOINTMENT" a JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
            WHERE a."appointment_id" = ?
            """, appointmentId);
        if (appts.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在");
        Map<String, Object> appt = appts.getFirst();
        String apptStatus = String.valueOf(appt.get("status"));
        if (!"BOOKED".equals(apptStatus) && !"CONFIRMED".equals(apptStatus))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前预约状态不可签到：" + apptStatus);
        if (user.clinicId() != null && !user.isHq()
                && !user.clinicId().equals(((Number) appt.get("clinic_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能签到本诊所的预约");
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM \"ENCOUNTER\" WHERE \"appointment_id\" = ?", Integer.class, appointmentId);
        if (existing != null && existing > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该预约已签到");
        Long encounterId = jdbc.queryForObject("""
            INSERT INTO "ENCOUNTER" ("appointment_id","status","arrived_at","arrived_by_account_id")
            VALUES (?, 'ARRIVED', current_timestamp, ?) RETURNING "encounter_id"
            """, Long.class, appointmentId, user.accountId());
        jdbc.update("UPDATE \"APPOINTMENT\" SET \"status\"='ARRIVED' WHERE \"appointment_id\"=?", appointmentId);
        jdbc.update("""
            INSERT INTO "APPOINTMENT_EVENT" ("appointment_id","old_status","new_status","old_shift_id","new_shift_id",
                "reason","operator_account_id","occurred_at")
            VALUES (?, ?, 'ARRIVED', (SELECT "shift_id" FROM "APPOINTMENT" WHERE "appointment_id"=?),
                (SELECT "shift_id" FROM "APPOINTMENT" WHERE "appointment_id"=?), 'CHECK_IN', ?, current_timestamp)
            """, appointmentId, apptStatus, appointmentId, appointmentId, user.accountId());
        return jdbc.queryForMap("""
            SELECT "encounter_id","appointment_id","status","arrived_at" FROM "ENCOUNTER" WHERE "encounter_id"=?
            """, encounterId);
    }

    /**
     * 更新就诊：可修改主诉/诊断/特殊需求，并驱动状态流转。
     * - ARRIVED -> IN_PROGRESS：班次所属牙医开始就诊；
     * - IN_PROGRESS/ARRIVED -> COMPLETED：班次所属牙医完成就诊，预约同步 COMPLETED；
     * - -> VOID：前台/负责人/集团管理员作废（未完成的就诊）。
     */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody UpdateRequest r) {
        CurrentUser user = AccessGuard.user();
        Map<String, Object> encounter = loadScoped(id, user);
        String current = String.valueOf(encounter.get("status"));
        String target = r.status() == null ? current : r.status().toUpperCase();

        if (r.chiefComplaint() != null || r.diagnosis() != null || r.specialNeeds() != null) {
            jdbc.update("""
                UPDATE "ENCOUNTER" SET
                    "chief_complaint" = COALESCE(?, "chief_complaint"),
                    "diagnosis" = COALESCE(?, "diagnosis"),
                    "special_needs" = COALESCE(?, "special_needs")
                WHERE "encounter_id" = ?
                """, r.chiefComplaint(), r.diagnosis(), r.specialNeeds(), id);
        }

        if (!target.equals(current)) {
            long appointmentId = ((Number) encounter.get("appointment_id")).longValue();
            switch (target) {
                case "IN_PROGRESS" -> {
                    requireShiftDentist(user, encounter);
                    if (!"ARRIVED".equals(current))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已签到就诊可以开始");
                    jdbc.update("UPDATE \"ENCOUNTER\" SET \"status\"='IN_PROGRESS' WHERE \"encounter_id\"=?", id);
                }
                case "COMPLETED" -> {
                    requireShiftDentist(user, encounter);
                    if (!"ARRIVED".equals(current) && !"IN_PROGRESS".equals(current))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可完成");
                    // 事件 old_status 取预约当前状态（APPOINTMENT_EVENT 约束只认预约状态集）
                    String apptStatus = String.valueOf(jdbc.queryForMap(
                            "SELECT \"status\" FROM \"APPOINTMENT\" WHERE \"appointment_id\"=?", appointmentId).get("status"));
                    jdbc.update("""
                        UPDATE "ENCOUNTER" SET "status"='COMPLETED', "confirmed_by_dentist_id"=?, "completed_at"=current_timestamp
                        WHERE "encounter_id"=?
                        """, user.staffId(), id);
                    jdbc.update("UPDATE \"APPOINTMENT\" SET \"status\"='COMPLETED' WHERE \"appointment_id\"=?", appointmentId);
                    jdbc.update("""
                        INSERT INTO "APPOINTMENT_EVENT" ("appointment_id","old_status","new_status","old_shift_id","new_shift_id",
                            "reason","operator_account_id","occurred_at")
                        VALUES (?, ?, 'COMPLETED', (SELECT "shift_id" FROM "APPOINTMENT" WHERE "appointment_id"=?),
                            (SELECT "shift_id" FROM "APPOINTMENT" WHERE "appointment_id"=?), 'ENCOUNTER_COMPLETED', ?, current_timestamp)
                        """, appointmentId, apptStatus, appointmentId, appointmentId, user.accountId());
                }
                case "VOID" -> {
                    AccessGuard.requireRole("RECEPTION", "MANAGER");
                    if ("COMPLETED".equals(current))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "已完成的就诊不可作废");
                    jdbc.update("UPDATE \"ENCOUNTER\" SET \"status\"='VOID' WHERE \"encounter_id\"=?", id);
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的就诊状态：" + target);
            }
        }
        return jdbc.queryForMap("""
            SELECT "encounter_id","appointment_id","status","chief_complaint","diagnosis","special_needs",
                   "arrived_at","completed_at","confirmed_by_dentist_id" FROM "ENCOUNTER" WHERE "encounter_id"=?
            """, id);
    }

    /**
     * 登记治疗项目：价格快照取当前有效价目（PRICE_VERSION 最新版），
     * 名称/单位/单价一并快照，保证账单与历史一致。仅班次所属牙医可登记。
     */
    @PostMapping("/{id}/treatment-lines")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addTreatmentLine(@PathVariable long id, @Valid @RequestBody TreatmentLineRequest r) {
        CurrentUser user = AccessGuard.requireRole("DENTIST");
        Map<String, Object> encounter = loadScoped(id, user);
        requireShiftDentist(user, encounter);
        String status = String.valueOf(encounter.get("status"));
        if ("VOID".equals(status) || "COMPLETED".equals(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前就诊状态不可登记治疗项目");
        if (jdbc.queryForObject("SELECT count(*) FROM \"INVOICE\" WHERE \"encounter_id\"=?", Integer.class, id) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该就诊已生成账单，不能修改治疗项目");

        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT ti."name", ti."active" FROM "TREATMENT_ITEM" ti WHERE ti."treatment_item_id" = ?
            """, r.treatmentItemId());
        if (items.isEmpty() || !Boolean.TRUE.equals(items.getFirst().get("active")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "治疗项目不存在或已停用");
        List<Map<String, Object>> prices = jdbc.queryForList("""
            SELECT "price_version_id","unit","unit_price" FROM "PRICE_VERSION"
            WHERE "treatment_item_id" = ? AND "valid_from" <= CURRENT_DATE
              AND ("valid_to" IS NULL OR "valid_to" >= CURRENT_DATE)
            ORDER BY "version_no" DESC LIMIT 1
            """, r.treatmentItemId());
        if (prices.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该治疗项目没有当前有效的价目");
        Map<String, Object> price = prices.getFirst();

        return jdbc.queryForMap("""
            INSERT INTO "TREATMENT_LINE" ("encounter_id","treatment_item_id","price_version_id","quantity",
                "name_snapshot","unit_snapshot","unit_price_snapshot","notes")
            VALUES (?,?,?,?,?,?,?,?)
            RETURNING "treatment_line_id","treatment_item_id","name_snapshot","quantity","unit_snapshot","unit_price_snapshot"
            """, id, r.treatmentItemId(), price.get("price_version_id"), r.quantity(),
                items.getFirst().get("name"), price.get("unit"), price.get("unit_price"), r.notes());
    }

    /** 删除未结算的治疗项目行。 */
    @DeleteMapping("/{id}/treatment-lines/{lineId}")
    @Transactional
    public Map<String, Object> removeTreatmentLine(@PathVariable long id, @PathVariable long lineId) {
        CurrentUser user = AccessGuard.requireRole("DENTIST");
        Map<String, Object> encounter = loadScoped(id, user);
        requireShiftDentist(user, encounter);
        if (jdbc.queryForObject("SELECT count(*) FROM \"INVOICE\" WHERE \"encounter_id\"=?", Integer.class, id) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该就诊已生成账单，不能修改治疗项目");
        int n = jdbc.update("DELETE FROM \"TREATMENT_LINE\" WHERE \"treatment_line_id\"=? AND \"encounter_id\"=?", lineId, id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "治疗项目行不存在");
        return Map.of("deleted", true, "treatmentLineId", lineId);
    }

    // ---------- 内部工具 ----------

    private static final String LIST_SQL = """
            SELECT e."encounter_id", e."appointment_id", e."status", e."chief_complaint", e."diagnosis",
                   e."special_needs", e."arrived_at", e."completed_at", e."confirmed_by_dentist_id",
                   a."patient_id", p."name" AS patient_name, s."dentist_id", st."name" AS dentist_name, s."clinic_id"
            FROM "ENCOUNTER" e
            JOIN "APPOINTMENT" a ON a."appointment_id" = e."appointment_id"
            JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
            JOIN "SHIFT" s ON s."shift_id" = a."shift_id"
            JOIN "STAFF" st ON st."staff_id" = s."dentist_id"
            """;

    /** 读取就诊并做数据范围校验。 */
    private Map<String, Object> loadScoped(long id, CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(LIST_SQL + " WHERE e.\"encounter_id\" = ?", id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "就诊记录不存在");
        Map<String, Object> row = rows.getFirst();
        if (user.patientId() != null && !user.patientId().equals(((Number) row.get("patient_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看本人的就诊记录");
        if (user.patientId() == null && !user.isHq()) {
            Long clinicId = user.clinicId();
            Long ownDentistId = user.hasRole("DENTIST") ? AccessGuard.ownDentistId(jdbc) : null;
            boolean inClinic = clinicId != null && clinicId.equals(((Number) row.get("clinic_id")).longValue());
            boolean ownShift = ownDentistId != null && ownDentistId.equals(((Number) row.get("dentist_id")).longValue());
            if (!inClinic && !ownShift)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本诊所范围内的就诊记录");
        }
        return row;
    }

    /** 要求当前用户是就诊对应班次的牙医。 */
    private void requireShiftDentist(CurrentUser user, Map<String, Object> encounter) {
        Long ownDentistId = AccessGuard.ownDentistId(jdbc);
        Long shiftDentistId = ((Number) encounter.get("dentist_id")).longValue();
        if (ownDentistId == null || !ownDentistId.equals(shiftDentistId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有该班次的牙医可以执行此操作");
    }

    public record UpdateRequest(String status, String chiefComplaint, String diagnosis, String specialNeeds) {}

    public record TreatmentLineRequest(@NotNull Long treatmentItemId, @NotNull @Positive BigDecimal quantity,
                                       String notes) {}
}
