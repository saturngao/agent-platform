package com.aip.platform.common;

/**
 * 平台通用常量
 */
public final class Constants {

    private Constants() {
    }

    /** 超级管理员角色标识（拥有全部权限，不做额外校验） */
    public static final String SUPER_ADMIN = "admin";

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 正常 / 停用 */
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DISABLED = 1;

    /** 删除标记 */
    public static final int DEL_FLAG_NORMAL = 0;
    public static final int DEL_FLAG_DELETED = 2;

    /** 数据来源（调用大模型的业务类型） */
    public interface BizType {
        String WORKFLOW = "workflow";
        String AGENT = "agent";
        String SUMMARY = "summary";
        String TEST = "test";
    }

    /** 数据权限范围 */
    public interface DataScope {
        String ALL = "1";
        String CUSTOM = "2";
        String DEPT = "3";
        String DEPT_AND_CHILD = "4";
        String SELF = "5";
    }
}
