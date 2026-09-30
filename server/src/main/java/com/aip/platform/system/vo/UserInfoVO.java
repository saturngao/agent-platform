package com.aip.platform.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户完整信息（基本信息 + 角色 + 权限）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基本信息 */
    private UserProfileVO user;

    /** 角色标识集合 */
    private List<String> roles;

    /** 权限标识集合 */
    private List<String> permissions;
}
