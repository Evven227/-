package com.adc.dental.inventory;

import com.adc.dental.auth.AccessGuard;
import com.adc.dental.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 库存模块：按诊所查询物料库存与出入库记录，支持入库/领用/盘点调整。
 * 只做库存数量管理，不做采购、供应商、财务成本核算。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final JdbcTemplate jdbc;

    public InventoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 库存列表：诊所员工看本诊所；集团管理员可通过 clinicId 指定或看全部。
     * lowStock=true 时只返回低于预警值的物料。
     */
    @GetMapping
    public List<Map<String, Object>> stock() {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        Long clinicId = user.isHq() ? null : user.clinicId();
        if (clinicId != null) {
            return jdbc.queryForList(STOCK_SQL + " WHERE c.\"clinic_id\" = ?" +
                    " ORDER BY m.\"material_code\"", clinicId);
        }
        return jdbc.queryForList(STOCK_SQL + " ORDER BY c.\"clinic_id\", m.\"material_code\"");
    }

    @GetMapping("/low-stock")
    public List<Map<String, Object>> lowStock() {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        Long clinicId = user.isHq() ? null : user.clinicId();
        if (clinicId != null) {
            return jdbc.queryForList(STOCK_SQL + " WHERE c.\"clinic_id\" = ? AND c.\"on_hand\" <= c.\"warning_threshold\"" +
                    " ORDER BY m.\"material_code\"", clinicId);
        }
        return jdbc.queryForList(STOCK_SQL + " WHERE c.\"on_hand\" <= c.\"warning_threshold\"" +
                " ORDER BY c.\"clinic_id\", m.\"material_code\"");
    }

    /** 出入库记录：可按诊所过滤，默认最近 100 条。 */
    @GetMapping("/movements")
    public List<Map<String, Object>> movements() {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        Long clinicId = user.isHq() ? null : user.clinicId();
        if (clinicId != null) {
            return jdbc.queryForList(MOVEMENT_SQL + " WHERE m.\"clinic_id\" = ?" +
                    " ORDER BY m.\"occurred_at\" DESC, m.\"movement_id\" DESC LIMIT 100", clinicId);
        }
        return jdbc.queryForList(MOVEMENT_SQL + " ORDER BY m.\"occurred_at\" DESC, m.\"movement_id\" DESC LIMIT 100");
    }

    /**
     * 登记库存变动：
     * - INBOUND 入库：数量为正，库存增加；
     * - USAGE 领用：数量为正，库存减少，不允许减成负数；
     * - ADJUSTMENT 盘点调整：quantity_delta 直接按传入数量（可为负）。
     * 更新使用乐观锁（lock_version），冲突返回 409。
     */
    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createMovement(@Valid @RequestBody MovementRequest r) {
        CurrentUser user = AccessGuard.requireRole("RECEPTION", "MANAGER");
        Long clinicId = r.clinicId();
        if (clinicId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须指定诊所");
        if (!user.isHq() && !clinicId.equals(user.clinicId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本诊所的库存");
        if (jdbc.queryForObject("SELECT count(*) FROM \"CLINIC\" WHERE \"clinic_id\"=?", Integer.class, clinicId) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "诊所不存在");
        if (jdbc.queryForObject("SELECT count(*) FROM \"MATERIAL\" WHERE \"material_id\"=? AND \"active\"=TRUE",
                Integer.class, r.materialId()) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "物料不存在或已停用");

        BigDecimal delta;
        switch (r.movementType()) {
            case "INBOUND" -> {
                if (r.quantity() == null || r.quantity().signum() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库数量必须为正数");
                delta = r.quantity();
            }
            case "USAGE" -> {
                if (r.quantity() == null || r.quantity().signum() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "领用数量必须为正数");
                delta = r.quantity().negate();
            }
            case "ADJUSTMENT" -> {
                if (r.quantity() == null || r.quantity().signum() == 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "盘点调整数量不能为零");
                delta = r.quantity();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的变动类型：" + r.movementType() + "（可选 INBOUND / USAGE / ADJUSTMENT）");
        }

        List<Map<String, Object>> stocks = jdbc.queryForList("""
            SELECT "on_hand", "lock_version" FROM "CLINIC_STOCK" WHERE "clinic_id" = ? AND "material_id" = ?
            """, clinicId, r.materialId());
        if (stocks.isEmpty()) {
            if (delta.signum() < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该诊所尚无此物料库存，不能做出库调整");
            jdbc.update("""
                INSERT INTO "CLINIC_STOCK" ("clinic_id","material_id","on_hand","warning_threshold","lock_version")
                VALUES (?,?,?,10,0)
                """, clinicId, r.materialId(), delta);
        } else {
            Map<String, Object> stock = stocks.getFirst();
            int lockVersion = ((Number) stock.get("lock_version")).intValue();
            BigDecimal onHand = (BigDecimal) stock.get("on_hand");
            if (onHand.add(delta).signum() < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "库存不足：当前 " + onHand + "，本次变动 " + delta);
            int updated = jdbc.update("""
                UPDATE "CLINIC_STOCK" SET "on_hand" = "on_hand" + ?, "lock_version" = "lock_version" + 1
                WHERE "clinic_id" = ? AND "material_id" = ? AND "lock_version" = ?
                """, delta, clinicId, r.materialId(), lockVersion);
            if (updated == 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "库存并发冲突，请刷新后重试");
        }
        return jdbc.queryForMap("""
            INSERT INTO "STOCK_MOVEMENT" ("clinic_id","material_id","movement_type","quantity_delta","reason",
                "occurred_at","operator_account_id")
            VALUES (?,?,?,?,?,current_timestamp,?)
            RETURNING "movement_id","clinic_id","material_id","movement_type","quantity_delta","reason","occurred_at"
            """, clinicId, r.materialId(), r.movementType(), delta, r.reason(), user.accountId());
    }

    // ---------- 内部工具 ----------

    private static final String STOCK_SQL = """
            SELECT c."clinic_id", cl."name" AS clinic_name, c."material_id", m."material_code", m."name" AS material_name,
                   m."unit", c."on_hand", c."warning_threshold", c."lock_version",
                   (c."on_hand" <= c."warning_threshold") AS low_stock
            FROM "CLINIC_STOCK" c
            JOIN "CLINIC" cl ON cl."clinic_id" = c."clinic_id"
            JOIN "MATERIAL" m ON m."material_id" = c."material_id"
            """;

    private static final String MOVEMENT_SQL = """
            SELECT m."movement_id", m."clinic_id", cl."name" AS clinic_name, m."material_id",
                   mt."name" AS material_name, mt."unit", m."movement_type", m."quantity_delta",
                   m."reason", m."occurred_at", m."operator_account_id"
            FROM "STOCK_MOVEMENT" m
            JOIN "CLINIC" cl ON cl."clinic_id" = m."clinic_id"
            JOIN "MATERIAL" mt ON mt."material_id" = m."material_id"
            """;

    public record MovementRequest(@NotNull Long clinicId, @NotNull Long materialId, @NotBlank String movementType,
                                  BigDecimal quantity, @NotBlank String reason) {}
}
