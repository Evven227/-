package com.adc.dental.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发与解析（HS256）。
 * 密钥与有效期通过环境变量 ADC_JWT_SECRET / ADC_JWT_TTL_HOURS 配置，
 * 未设置时使用课程演示默认值，仅适合课堂演示，不适合生产。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${adc.jwt.secret:adc-dental-demo-secret-please-change-2026}") String secret,
                      @Value("${adc.jwt.ttl-hours:12}") long ttlHours) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256 要求密钥至少 256 bit，不足则补足，避免演示环境启动失败
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            secretBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttl = Duration.ofHours(Math.max(1, ttlHours));
    }

    /** 签发 token：subject=accountId，claims 带 loginName / staffId / patientId / grants。 */
    public String issue(CurrentUser user) {
        Instant now = Instant.now();
        List<Map<String, Object>> grants = new ArrayList<>();
        for (CurrentUser.Grant g : user.grants()) {
            grants.add(Map.of(
                    "role", g.roleCode(),
                    "scope", g.scopeType(),
                    "clinicId", g.clinicId() == null ? "" : g.clinicId()));
        }
        return Jwts.builder()
                .subject(String.valueOf(user.accountId()))
                .claim("loginName", user.loginName())
                .claim("staffId", user.staffId() == null ? "" : user.staffId())
                .claim("patientId", user.patientId() == null ? "" : user.patientId())
                .claim("grants", grants)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 token；过期或签名不合法时抛出运行时异常，由拦截器统一转 401。 */
    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Long staffId = longClaim(claims, "staffId");
        Long patientId = longClaim(claims, "patientId");
        List<CurrentUser.Grant> grants = new ArrayList<>();
        Object raw = claims.get("grants");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    grants.add(new CurrentUser.Grant(
                            String.valueOf(m.get("role")),
                            String.valueOf(m.get("scope")),
                            longClaim(m.get("clinicId"))));
                }
            }
        }
        return new CurrentUser(Long.valueOf(claims.getSubject()), String.valueOf(claims.get("loginName")),
                staffId, patientId, grants);
    }

    private static Long longClaim(Claims claims, String name) {
        return longClaim(claims.get(name));
    }

    private static Long longClaim(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
