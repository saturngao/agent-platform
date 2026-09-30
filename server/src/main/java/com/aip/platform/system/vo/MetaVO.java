package com.aip.platform.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 前端路由 meta 信息（vue-element-admin 惯例）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由标题 */
    private String title;

    /** 图标 */
    private String icon;

    /** 是否不缓存 */
    private boolean noCache;
}
