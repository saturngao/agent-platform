package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    private Long deptId;

    private String username;

    private String nickname;

    private String userType;

    private String email;

    private String phonenumber;

    private Integer sex;

    private String avatar;

    private String password;

    /** 0 正常 1 停用 */
    private Integer status;

    @TableLogic(value = "0", delval = "2")
    private Integer delFlag;

    private String loginIp;

    private LocalDateTime loginDate;

    // ---------- 非数据库字段 ----------
    @TableField(exist = false)
    private String deptName;

    /** 查询条件：按部门及其下级 */
    @TableField(exist = false)
    private Long scopeDeptId;

    @TableField(exist = false)
    private List<Long> roleIds;

    @TableField(exist = false)
    private List<Long> postIds;

    /** 查询条件：创建时间范围 */
    @TableField(exist = false)
    private String beginTime;

    @TableField(exist = false)
    private String endTime;
}
