package com.adc.dental.auth;

/**
 * 当前请求的用户上下文（ThreadLocal），由 AuthInterceptor 在 preHandle 写入、
 * afterCompletion 清理。控制器通过 AccessGuard 间接访问，不直接调用本类。
 */
public final class UserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    static void clear() {
        HOLDER.remove();
    }
}
