package com.aip.platform.system.controller;

import com.aip.platform.common.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * 权限校验器：供 @PreAuthorize 表达式调用，bean 名称为 permissionChecker
 *
 * <pre>
 * 用法：@PreAuthorize("@permissionChecker.has('system:user:add')")
 * </pre>
 */
@Component("permissionChecker")
public class PermissionChecker {

    /**
     * 判断当前登录用户是否拥有指定权限
     *
     * @param perms 权限标识，如 system:user:list
     * @return true 表示放行，超级管理员恒为 true
     */
    public boolean has(String perms) {
        if (SecurityUtils.isSuperAdmin()) {
            return true;
        }
        return SecurityUtils.hasPermission(perms);
    }
}
