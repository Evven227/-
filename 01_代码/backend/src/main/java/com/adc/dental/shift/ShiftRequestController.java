package com.adc.dental.shift;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-requests")
public class ShiftRequestController {
    private final JdbcTemplate jdbc;

    public ShiftRequestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 调班申请列表：
     * - 牙医：只看自己提交的申请；
     * - 前台 / 诊所负责人 / 集团管理员：看全部（调班可能影响跨诊所预约）。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        CurrentUser user = AccessGuard.user();
        if (user.isPatient()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "患者无权查看调班申请");
        }
        if (user.hasRole("DENTIST") && !user.isHq() && !user.hasRole("MANAGER")) {
            Long ownDentistId = AccessGuard.ownDentistId(jdbc);
            if (ownDentistId == null) return List.of();
            return jdbc.queryForList(LIST_SQL + " WHERE r.\"applicant_dentist_id\" = ?" +
                    " ORDER BY r.\"submitted_at\" DESC, r.\"request_id\" DESC", ownDentistId);
        }
        return jdbc.queryForList(LIST_SQL + " ORDER BY r.\"submitted_at\" DESC, r.\"request_id\" DESC");
    }

    /** 申请详情：含 SHIFT_REQUEST_ITEM 建议项与受影响预约。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        AccessGuard.user();
        List<Map<String, Object>> rows = jdbc.queryForList(LIST_SQL + " WHERE r.\"request_id\" = ?", id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调班申请不存在");
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT i."shift_id", i."proposed_dentist_id", i."proposed_clinic_id",
                   i."proposed_starts_at", i."proposed_ends_at", i."result_shift_id",
                   s."starts_at" AS current_starts_at, s."ends_at" AS current_ends_at,
                   s."dentist_id" AS current_dentist_id, s."clinic_id" AS current_clinic_id
            FROM "SHIFT_REQUEST_ITEM" i JOIN "SHIFT" s ON s."shift_id" = i."shift_id"
            WHERE i."request_id" = ?
            """, id);
        List<Map<String, Object>> impacts = jdbc.queryForList("""
            SELECT x."appointment_id", x."resolution_status", a."starts_at", a."ends_at", a."status",
                   p."name" AS patient_name, a."patient_id"
            FROM "REQUEST_APPOINTMENT_IMPACT" x
            JOIN "APPOINTMENT" a ON a."appointment_id" = x."appointment_id"
            JOIN "PATIENT" p ON p."patient_id" = a."patient_id"
            WHERE x."request_id" = ?
            ORDER BY a."starts_at"
            """, id);
        Map<String, Object> result = new java.util.LinkedHashMap<>(rows.getFirst());
        result.put("items", items);
        result.put("impacts", impacts);
        return result;
    }

    /**
     * 牙医提交调班/请假申请，申请人锁定为登录账号对应的牙医。
     * 新版表单只选两个日期：「需要调班的日期」+「补班日期」，
     * 分别写入 requested_date / swap_target_date；requested_from/to 由需要调班的日期推导，
     * 供旧列表与统计沿用。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(@Valid @RequestBody Request r) {
        AccessGuard.requireRole("DENTIST");
        Long applicantDentistId = AccessGuard.ownDentistId(jdbc);
        if (applicantDentistId == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是牙医档案");

        LocalDate reqDate = r.requestedDate();
        LocalDate swapDate = r.swapTargetDate();
        LocalDateTime from;
        LocalDateTime to;
        if (reqDate != null) {
            if (reqDate.isBefore(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需要调班的日期不能早于今天");
            if (swapDate != null && swapDate.isBefore(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补班日期不能早于今天");
            from = reqDate.atTime(0, 0);
            to = reqDate.atTime(23, 59);
        } else {
            if (r.requestedFrom() == null || r.requestedTo() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择需要调班的日期");
            if (r.requestedTo().isBefore(r.requestedFrom()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
            from = r.requestedFrom();
            to = r.requestedTo();
        }
        return jdbc.queryForMap("""
            INSERT INTO "SHIFT_REQUEST" ("applicant_dentist_id","request_type","requested_from","requested_to","reason","status","submitted_at","requested_date","swap_target_date")
            VALUES (?, ?, ?, ?, ?, 'SUBMITTED', current_timestamp, ?, ?)
            RETURNING "request_id","applicant_dentist_id","request_type","requested_from","requested_to","reason","status","requested_date","swap_target_date"
            """, applicantDentistId, r.requestType(), Timestamp.valueOf(from), Timestamp.valueOf(to), r.reason(),
                reqDate, swapDate);
    }

    /**
     * 审核调班申请（诊所负责人 / 集团管理员）：
     * - REJECT：驳回，需填写驳回原因；
     * - COORDINATING：登记协调建议（替换 SHIFT_REQUEST_ITEM）；
     * - IMPLEMENTED：实施——按建议生成班次新版本（旧版 SUPERSEDED），
     *   平移受影响预约并写 APPOINTMENT_EVENT，处理受影响预约的 REQUEST_APPOINTMENT_IMPACT。
     */
    @PutMapping("/{id}/review")
    @Transactional
    public Map<String, Object> review(@PathVariable long id, @Valid @RequestBody ReviewRequest r) {
        CurrentUser user = AccessGuard.requireRole("MANAGER");
        if (user.staffId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "审核人不是员工账号");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"status\" FROM \"SHIFT_REQUEST\" WHERE \"request_id\" = ?", id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调班申请不存在");
        String status = String.valueOf(rows.getFirst().get("status"));
        String action = r.action().toUpperCase();

        switch (action) {
            case "REJECT" -> {
                if (!"SUBMITTED".equals(status) && !"COORDINATING".equals(status))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可驳回");
                if (r.rejectionReason() == null || r.rejectionReason().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回必须填写原因");
                jdbc.update("""
                    UPDATE "SHIFT_REQUEST" SET "status"='REJECTED', "reviewer_staff_id"=?,
                        "reviewed_at"=current_timestamp, "rejection_reason"=?
                    WHERE "request_id"=?
                    """, user.staffId(), r.rejectionReason(), id);
            }
            case "COORDINATING" -> {
                if (!"SUBMITTED".equals(status))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "只有 SUBMITTED 状态可进入协调");
                replaceItems(id, r.items());
                jdbc.update("""
                    UPDATE "SHIFT_REQUEST" SET "status"='COORDINATING', "reviewer_staff_id"=?,
                        "reviewed_at"=current_timestamp
                    WHERE "request_id"=?
                    """, user.staffId(), id);
            }
            case "IMPLEMENTED" -> {
                if (!"SUBMITTED".equals(status) && !"COORDINATING".equals(status))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可实施");
                if (r.items() == null || r.items().isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实施必须提供协调建议项");
                replaceItems(id, r.items());
                List<Map<String, Object>> items = jdbc.queryForList("""
                    SELECT i."shift_id", i."proposed_dentist_id", i."proposed_clinic_id",
                           i."proposed_starts_at", i."proposed_ends_at"
                    FROM "SHIFT_REQUEST_ITEM" i WHERE i."request_id" = ?
                    """, id);
                List<Long> movedAppointments = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    movedAppointments.addAll(implementShiftChange(id, user, item));
                }
                resolveImpacts(id, user, movedAppointments);
                jdbc.update("""
                    UPDATE "SHIFT_REQUEST" SET "status"='IMPLEMENTED', "reviewer_staff_id"=?,
                        "reviewed_at"=current_timestamp
                    WHERE "request_id"=?
                    """, user.staffId(), id);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的操作：" + action + "（可选 REJECT / COORDINATING / IMPLEMENTED）");
        }
        return jdbc.queryForMap("SELECT \"request_id\",\"status\",\"reviewer_staff_id\",\"reviewed_at\",\"rejection_reason\" FROM \"SHIFT_REQUEST\" WHERE \"request_id\"=?", id);
    }

    // ---------- 内部工具 ----------

    private static final String LIST_SQL = """
            SELECT r."request_id", r."applicant_dentist_id", st."name" AS applicant_name,
                   r."request_type", r."requested_from", r."requested_to", r."reason", r."status",
                   r."submitted_at", r."reviewer_staff_id", rv."name" AS reviewer_name,
                   r."reviewed_at", r."rejection_reason",
                   r."requested_date", r."swap_target_date"
            FROM "SHIFT_REQUEST" r
            JOIN "DENTIST" d ON d."dentist_id" = r."applicant_dentist_id"
            JOIN "STAFF" st ON st."staff_id" = d."dentist_id"
            LEFT JOIN "STAFF" rv ON rv."staff_id" = r."reviewer_staff_id"
            """;

    private void replaceItems(long requestId, List<ReviewRequest.Item> items) {
        jdbc.update("DELETE FROM \"SHIFT_REQUEST_ITEM\" WHERE \"request_id\" = ?", requestId);
        if (items == null) return;
        for (ReviewRequest.Item item : items) {
            if (item.shiftId() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "协调建议必须指定班次");
            jdbc.update("""
                INSERT INTO "SHIFT_REQUEST_ITEM" ("request_id","shift_id","proposed_dentist_id",
                    "proposed_clinic_id","proposed_starts_at","proposed_ends_at")
                VALUES (?,?,?,?,?,?)
                """, requestId, item.shiftId(), item.proposedDentistId(), item.proposedClinicId(),
                    item.proposedStartsAt() == null ? null : Timestamp.valueOf(item.proposedStartsAt()),
                    item.proposedEndsAt() == null ? null : Timestamp.valueOf(item.proposedEndsAt()));
        }
    }

    /**
     * 实施单个班次调整：生成新版本班次，旧版置 SUPERSEDED，
     * 受影响预约平移到新班次并写 APPOINTMENT_EVENT。返回受影响的预约 ID 列表。
     */
    private List<Long> implementShiftChange(long requestId, CurrentUser user, Map<String, Object> item) {
        Long oldShiftId = ((Number) item.get("shift_id")).longValue();
        List<Map<String, Object>> shiftRows = jdbc.queryForList("""
            SELECT "shift_series_no","dentist_id","clinic_id","starts_at","ends_at","version_no","status"
            FROM "SHIFT" WHERE "shift_id" = ?
            """, oldShiftId);
        if (shiftRows.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "班次不存在：" + oldShiftId);
        Map<String, Object> old = shiftRows.getFirst();
        if ("CANCELLED".equals(String.valueOf(old.get("status"))) || "SUPERSEDED".equals(String.valueOf(old.get("status"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "班次已失效，不能实施调整：" + oldShiftId);

        LocalDateTime oldStarts = ((Timestamp) old.get("starts_at")).toLocalDateTime();
        LocalDateTime oldEnds = ((Timestamp) old.get("ends_at")).toLocalDateTime();
        Long newDentistId = item.get("proposed_dentist_id") == null
                ? ((Number) old.get("dentist_id")).longValue() : ((Number) item.get("proposed_dentist_id")).longValue();
        Long newClinicId = item.get("proposed_clinic_id") == null
                ? ((Number) old.get("clinic_id")).longValue() : ((Number) item.get("proposed_clinic_id")).longValue();
        LocalDateTime newStarts = item.get("proposed_starts_at") == null
                ? oldStarts : ((Timestamp) item.get("proposed_starts_at")).toLocalDateTime();
        LocalDateTime newEnds = item.get("proposed_ends_at") == null
                ? oldEnds : ((Timestamp) item.get("proposed_ends_at")).toLocalDateTime();
        if (!newEnds.isAfter(newStarts))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调整后的班次结束时间必须晚于开始时间");
        int newVersion = ((Number) old.get("version_no")).intValue() + 1;

        // 顶班来源：新医生所属诊所与原班次诊所一致=同店，否则=跨店。
        Integer sameClinic = jdbc.queryForObject("""
            SELECT CASE WHEN "home_clinic_id" = ? THEN 1 ELSE 0 END FROM "STAFF" WHERE "staff_id" = ?
            """, Integer.class, newClinicId, newDentistId);
        String substituteFrom = (sameClinic != null && sameClinic == 1) ? "SAME_CLINIC" : "CROSS_CLINIC";

        // 关键：必须写 staff_id，患者端/前台端按 SHIFT.staff_id 关联医生姓名；
        // 同时补 shift_date/source/substitute_from/role_type/origin_request_id，
        // 使顶班日预约改派后医生显示、排班查询、绩效顶班计数全部生效。
        Long newShiftId = jdbc.queryForObject("""
            INSERT INTO "SHIFT" ("shift_series_no","dentist_id","staff_id","clinic_id","starts_at","ends_at",
                "status","version_no","previous_shift_id","published_by_staff_id","published_at",
                "shift_date","role_type","source","substitute_from","origin_request_id")
            VALUES (?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, current_timestamp, ?, 'DENTIST', 'SUBSTITUTE', ?, ?)
            RETURNING "shift_id"
            """, Long.class, old.get("shift_series_no"), newDentistId, newDentistId, newClinicId,
                Timestamp.valueOf(newStarts), Timestamp.valueOf(newEnds), newVersion, oldShiftId, user.staffId(),
                newStarts.toLocalDate(), substituteFrom, requestId);
        jdbc.update("UPDATE \"SHIFT\" SET \"status\"='SUPERSEDED' WHERE \"shift_id\"=?", oldShiftId);
        jdbc.update("UPDATE \"SHIFT_REQUEST_ITEM\" SET \"result_shift_id\"=? WHERE \"request_id\"=? AND \"shift_id\"=?",
                newShiftId, requestId, oldShiftId);

        // 平移未完结的预约到新班次，时间按相同差值平移
        Duration delta = Duration.between(oldStarts, newStarts);
        List<Map<String, Object>> affected = jdbc.queryForList("""
            SELECT "appointment_id","patient_id","starts_at","ends_at","status"
            FROM "APPOINTMENT" WHERE "shift_id" = ? AND "status" NOT IN ('CANCELLED','COMPLETED','NO_SHOW')
            ORDER BY "appointment_id"
            """, oldShiftId);
        List<Long> appointmentIds = new ArrayList<>();
        for (Map<String, Object> a : affected) {
            long appointmentId = ((Number) a.get("appointment_id")).longValue();
            appointmentIds.add(appointmentId);
            LocalDateTime aStart = ((Timestamp) a.get("starts_at")).toLocalDateTime();
            LocalDateTime aEnd = ((Timestamp) a.get("ends_at")).toLocalDateTime();
            LocalDateTime targetStart = aStart.plus(delta);
            LocalDateTime targetEnd = aEnd.plus(delta);
            jdbc.update("""
                UPDATE "APPOINTMENT" SET "shift_id"=?, "starts_at"=?, "ends_at"=?, "status"='BOOKED'
                WHERE "appointment_id"=?
                """, newShiftId, Timestamp.valueOf(targetStart), Timestamp.valueOf(targetEnd), appointmentId);
            jdbc.update("""
                INSERT INTO "APPOINTMENT_EVENT" ("appointment_id","old_status","new_status","old_shift_id","new_shift_id",
                    "old_starts_at","old_ends_at","new_starts_at","new_ends_at","reason","operator_account_id","occurred_at")
                VALUES (?, ?, 'BOOKED', ?, ?, ?, ?, ?, ?, 'SHIFT_ADJUSTED', ?, current_timestamp)
                """, appointmentId, a.get("status"), oldShiftId, newShiftId,
                    Timestamp.valueOf(aStart), Timestamp.valueOf(aEnd),
                    Timestamp.valueOf(targetStart), Timestamp.valueOf(targetEnd), user.accountId());
        }
        return appointmentIds;
    }

    /** 受影响预约：已随班次平移的标记 RESOLVED；其余（如取消/缺席）标记 CONTACTED 待跟进。 */
    private void resolveImpacts(long requestId, CurrentUser user, List<Long> movedAppointments) {
        if (!movedAppointments.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(movedAppointments.size(), "?"));
            Object[] args = new Object[movedAppointments.size() + 2];
            args[0] = user.accountId();
            args[1] = requestId;
            for (int i = 0; i < movedAppointments.size(); i++) {
                args[i + 2] = movedAppointments.get(i);
            }
            jdbc.update("""
                UPDATE "REQUEST_APPOINTMENT_IMPACT"
                SET "resolution_status"='RESOLVED', "resolved_by_account_id"=?, "resolved_at"=current_timestamp
                WHERE "request_id"=? AND "appointment_id" IN (%s)
                """.formatted(placeholders), args);
        }
        jdbc.update("""
            UPDATE "REQUEST_APPOINTMENT_IMPACT"
            SET "resolution_status"='CONTACTED', "resolved_by_account_id"=?, "resolved_at"=current_timestamp
            WHERE "request_id"=? AND "resolution_status"='PENDING'
            """, user.accountId(), requestId);
    }

    public record Request(Long applicantDentistId, @NotBlank String requestType,
                          LocalDateTime requestedFrom, LocalDateTime requestedTo,
                          @NotBlank String reason,
                          LocalDate requestedDate, LocalDate swapTargetDate) {}

    public record ReviewRequest(@NotBlank String action, String rejectionReason, List<Item> items) {
        public record Item(Long shiftId, Long proposedDentistId, Long proposedClinicId,
                           @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class) LocalDateTime proposedStartsAt,
                           @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class) LocalDateTime proposedEndsAt) {}
    }
}
