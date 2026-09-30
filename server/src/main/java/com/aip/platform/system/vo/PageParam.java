package com.aip.platform.system.vo;

import com.aip.platform.common.PageQuery;

/**
 * 分页参数增强：支持「不分页全量返回」语义
 *
 * <p>约定：pageNum &lt;= 0 或 pageSize &lt;= 0 时表示不分页（下拉、树形下拉等场景）。
 * 由于 PageQuery 的 getter 会把非法值兜底成默认值，这里通过覆写 setter
 * 保留前端传入的原始值，供 Service 判断是否跳过分页。</p>
 */
public class PageParam extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 前端原始页码 */
    private Long rawPageNum;

    /** 前端原始每页条数 */
    private Long rawPageSize;

    @Override
    public void setPageNum(Long pageNum) {
        this.rawPageNum = pageNum;
        super.setPageNum(pageNum);
    }

    @Override
    public void setPageSize(Long pageSize) {
        this.rawPageSize = pageSize;
        super.setPageSize(pageSize);
    }

    /** 是否不分页（全量返回） */
    public boolean isNoPaging() {
        return (rawPageNum != null && rawPageNum <= 0) || (rawPageSize != null && rawPageSize <= 0);
    }

    /**
     * 统一判断入口，兼容直接传入 PageQuery 的场景
     *
     * @param query 分页参数
     * @return true 表示全量返回
     */
    public static boolean isNoPaging(PageQuery query) {
        return query instanceof PageParam param && param.isNoPaging();
    }
}
