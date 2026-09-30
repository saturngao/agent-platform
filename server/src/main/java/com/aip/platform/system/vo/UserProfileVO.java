package com.aip.platform.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前登录用户的基本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private Long deptId;

    private String deptName;
}
