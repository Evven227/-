package com.adc.dental;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：把错误的真实原因写进响应体的 message 字段。
 *
 * 背景：默认情况下 Spring 的错误响应只含 timestamp/status/error/path，
 * 不含 ResponseStatusException 的 reason，也不含 @Valid 校验的字段错误，
 * 导致前端只能显示"请求失败(4xx)"，用户看不到"账号或密码错误"、
 * "您在该时间段已有预约"等真实原因。这里统一补上 message。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 校验失败：返回第一个字段的中文提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("请求参数不合法，请检查必填项");
        return body(400, msg);
    }

    /** ResponseStatusException：把 reason 明确放进 message（与 include-message 双保险）。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> onResponseStatus(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "请求失败";
        return body(e.getStatusCode().value(), msg);
    }

    private ResponseEntity<Map<String, Object>> body(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
