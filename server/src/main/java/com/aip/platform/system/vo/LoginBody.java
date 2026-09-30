package com.aip.platform.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求体
 */
@Data
public class LoginBody implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    private String username;

    /** 登录密码 */
    private String password;

    /** 验证码（当前未启用，保留扩展位） */
    private String code;

    /** 验证码 UUID（当前未启用，保留扩展位） */
    private String uuid;
}
