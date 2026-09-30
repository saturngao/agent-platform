package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    private String roleName;

    private String roleKey;

    private Integer roleSort;

    /** 1全部 2自定义 3本部门 4本部门及以下 5仅本人 */
    private String dataScope;

    private Integer status;

    @TableLogic(value = "0", delval = "2")
    private Integer delFlag;

    // ---------- 非数据库字段 ----------
    @TableField(exist = false)
    private List<Long> menuIds;

    @TableField(exist = false)
    private List<Long> deptIds;

    /** 查询标记：当前用户是否已拥有该角色 */
    @TableField(exist = false)
    private Boolean flag;
}
