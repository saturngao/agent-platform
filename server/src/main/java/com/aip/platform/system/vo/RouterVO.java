package com.aip.platform.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 前端动态路由节点（vue-element-admin 惯例）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouterVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单ID */
    private Long id;

    /** 路由名称，前端 keep-alive 依赖它 */
    private String name;

    /** 路由地址 */
    private String path;

    /** 组件路径，顶层目录固定 Layout */
    private String component;

    /** 重定向地址 */
    private String redirect;

    /** 是否在侧边栏隐藏 */
    private boolean hidden;

    /** 是否始终显示根路由 */
    private boolean alwaysShow;

    /** 路由元信息 */
    private MetaVO meta;

    /** 子路由 */
    private List<RouterVO> children;
}
