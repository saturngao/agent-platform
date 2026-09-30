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
 * 部门
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class Dept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "dept_id", type = IdType.AUTO)
    private Long deptId;

    private Long parentId;

    private String ancestors;

    private String deptName;

    private String leader;

    private String phone;

    private String email;

    private Integer orderNum;

    private Integer status;

    @TableLogic(value = "0", delval = "2")
    private Integer delFlag;

    @TableField(exist = false)
    private List<Dept> children;
}
