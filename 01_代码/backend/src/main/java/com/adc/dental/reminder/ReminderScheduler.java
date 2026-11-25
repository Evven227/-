package com.adc.dental.reminder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 就诊提醒定时任务。
 *
 * 规则：预约成功后写入一条 PENDING 提醒，scheduled_at = 就诊开始时间前 12 小时。
 * 本任务每 60 秒扫描一次，把到点且仍有效的提醒置为 SENT（并写 sent_at）。
 * 已取消的预约在取消时由 Portal 层把提醒置为 CANCELLED，此处再兜一层：
 * 若预约已不是有效状态，则不发送并作废提醒，避免打扰已取消的患者。
 */
@Component
public class ReminderScheduler {

    private final JdbcTemplate jdbc;

    public ReminderScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)
    public void dispatchDueReminders() {
        // 1) 作废：预约已被取消/爽约/完成，提醒不再需要
        jdbc.update("""
                UPDATE "NOTIFICATION" n SET "status" = 'CANCELLED'
                 WHERE n."event_type" = 'APPT_REMINDER_12H' AND n."status" = 'PENDING'
                   AND n."ref_type" = 'APPOINTMENT'
                   AND EXISTS (SELECT 1 FROM "APPOINTMENT" a
                                WHERE a."appointment_id" = n."ref_id"
                                  AND a."status" NOT IN ('BOOKED','CONFIRMED','ARRIVED'))
                """);
        // 2) 发送：到点且预约仍有效
        jdbc.update("""
                UPDATE "NOTIFICATION" n SET "status" = 'SENT', "sent_at" = current_timestamp
                 WHERE n."event_type" = 'APPT_REMINDER_12H' AND n."status" = 'PENDING'
                   AND n."scheduled_at" <= current_timestamp
                   AND EXISTS (SELECT 1 FROM "APPOINTMENT" a
                                WHERE a."appointment_id" = n."ref_id"
                                  AND a."status" IN ('BOOKED','CONFIRMED','ARRIVED'))
                """);
    }
}
