package com.aip.platform.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录成功返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT 令牌 */
    private String token;

    /** 有效期（秒） */
    private long expiresIn;

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 部门ID */
    private Long deptId;
}
