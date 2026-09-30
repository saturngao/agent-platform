package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 菜单 / 权限
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class Menu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;

    private String menuName;

    private Long parentId;

    private Integer orderNum;

    private String path;

    private String component;

    private String query;

    /** 0 外链 1 非外链 */
    private Integer isFrame;

    /** 0 缓存 1 不缓存 */
    private Integer isCache;

    /** M 目录 C 菜单 F 按钮 */
    private String menuType;

    /** 0 显示 1 隐藏 */
    private Integer visible;

    private Integer status;

    private String perms;

    private String icon;

    @TableField(exist = false)
    private List<Menu> children;
}
