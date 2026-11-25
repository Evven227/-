package com.adc.dental.shift;

import com.adc.dental.auth.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 班次与可预约时间块。
 * 参考 OpenDental 的"椅位 + 医生 + 时间段"三维资源锁定：本系统以牙医已发布班次为资源，
 * 按固定粒度切成时间块，前端点选即可预约（无需手填分钟）。
 */
@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final JdbcTemplate jdbc;
    private static final int SLOT_MINUTES = 30;

    public ShiftController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 可用时间块：给定牙医与日期，返回该牙医当天所有 PUBLISHED 班次切成的时间块。
     * 每个块标注：是否已被占用（非取消/缺席的预约）、是否在过去。
     * 数据范围：患者/管理员看全部；诊所员工仅看本诊所牙医的班次。
     */
    @GetMapping("/available-slots")
    public List<Map<String, Object>> availableSlots(@RequestParam Long dentistId,
                                                   @RequestParam String date) {
        AccessGuard.user(); // 触发鉴权（白名单外必须登录）
        LocalDate day = LocalDate.parse(date);
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.atTime(23, 59, 59);
        List<Map<String, Object>> shifts = jdbc.queryForList("""
            SELECT s."shift_id", s."dentist_id", s."clinic_id", s."starts_at", s."ends_at"
            FROM "SHIFT" s
            WHERE s."dentist_id" = ? AND s."status" = 'PUBLISHED'
              AND s."starts_at" < ? AND s."ends_at" > ?
            ORDER BY s."starts_at", s."shift_id"
            """, dentistId, Timestamp.valueOf(dayEnd), Timestamp.valueOf(dayStart));

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (Map<String, Object> shift : shifts) {
            long shiftId = ((Number) shift.get("shift_id")).longValue();
            long dentist = ((Number) shift.get("dentist_id")).longValue();
            Object clinicObj = shift.get("clinic_id");
            Long clinicId = clinicObj == null ? null : ((Number) clinicObj).longValue();
            LocalDateTime sStart = ((Timestamp) shift.get("starts_at")).toLocalDateTime();
            LocalDateTime sEnd = ((Timestamp) shift.get("ends_at")).toLocalDateTime();
            LocalDateTime t = sStart;
            while (t.plusMinutes(SLOT_MINUTES).compareTo(sEnd) <= 0) {
                LocalDateTime slotStart = t;
                LocalDateTime slotEnd = t.plusMinutes(SLOT_MINUTES);
                boolean inPast = slotStart.isBefore(now);
                boolean occupied = isOccupied(shiftId, slotStart, slotEnd);
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("shiftId", shiftId);
                slot.put("dentistId", dentist);
                slot.put("clinicId", clinicId);
                slot.put("start", slotStart.toString());
                slot.put("end", slotEnd.toString());
                slot.put("available", !inPast && !occupied);
                slot.put("occupied", occupied);
                slot.put("past", inPast);
                slots.add(slot);
                t = slotEnd;
            }
        }
        return slots;
    }

    private boolean isOccupied(long shiftId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM "APPOINTMENT"
            WHERE "shift_id" = ? AND "status" NOT IN ('CANCELLED','NO_SHOW')
              AND "starts_at" < ? AND "ends_at" > ?
            """, Integer.class, shiftId, Timestamp.valueOf(slotEnd), Timestamp.valueOf(slotStart));
        return n != null && n > 0;
    }
}
