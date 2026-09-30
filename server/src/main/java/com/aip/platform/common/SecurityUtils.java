package com.aip.platform.common;

import com.aip.platform.common.constant.HttpStatus;
import com.aip.platform.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

/**
 * 安全与通用上下文工具类
 */
public final class SecurityUtils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private SecurityUtils() {
    }

    /** 获取当前登录认证信息 */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** 获取当前登录用户，未登录返回 null */
    public static LoginUser getLoginUser() {
        Authentication auth = getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    public static LoginUser requireLoginUser() {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static Long getUserId() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getUserId();
    }

    public static Long requireUserId() {
        return requireLoginUser().getUserId();
    }

    public static String getUsername() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getUsername();
    }

    public static String requireUsername() {
        return requireLoginUser().getUsername();
    }

    public static Long getDeptId() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getDeptId();
    }

    public static boolean isSuperAdmin() {
        LoginUser user = getLoginUser();
        return user != null && user.isSuperAdmin();
    }

    public static boolean hasPermission(String perms) {
        LoginUser user = getLoginUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return user.getPermissions() != null && user.getPermissions().contains(perms);
    }

    /** 密码加密 */
    public static String encryptPassword(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 密码比对：明文 vs 密文 */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        // 兼容历史明文口令
        if (encodedPassword.length() < 20) {
            return rawPassword.equals(encodedPassword);
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /** 填充创建人与创建时间 */
    public static void fillCreate(Object entity) {
        if (!(entity instanceof com.aip.platform.common.BaseEntity base)) {
            return;
        }
        String username = getUsername();
        if (username != null) {
            base.setCreateBy(username);
        }
        base.setCreateTime(LocalDateTime.now());
    }

    /** 填充更新人与更新时间 */
    public static void fillUpdate(Object entity) {
        if (!(entity instanceof com.aip.platform.common.BaseEntity base)) {
            return;
        }
        String username = getUsername();
        if (username != null) {
            base.setUpdateBy(username);
        }
        base.setUpdateTime(LocalDateTime.now());
    }
}
