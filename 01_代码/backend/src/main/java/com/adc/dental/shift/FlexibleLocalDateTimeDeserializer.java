package com.adc.dental.shift;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 宽容解析前端回传的时间字符串，兼容三种格式：
 *  - 带时区 ISO：2026-11-27T00:00:00.000+00:00 / 2026-11-27T00:00:00Z
 *  - 本地日期时间：2026-11-27T00:00:00 / 2026-11-27T00:00
 *  - 仅日期：2026-11-27
 * 避免浏览器缓存旧前端时回传带时区字符串导致 LocalDateTime 反序列化 500。
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception ignored) {
            // 继续尝试其它格式
        }
        throw new IOException("无法解析日期时间：" + raw);
    }
}
