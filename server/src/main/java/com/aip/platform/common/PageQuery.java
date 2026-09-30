package com.aip.platform.common;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页查询参数
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 500;

    private Long pageNum = (long) DEFAULT_PAGE_NUM;
    private Long pageSize = (long) DEFAULT_PAGE_SIZE;

    /** 排序字段，驼峰 */
    private String orderByColumn;

    /** asc / desc */
    private String isAsc = "asc";

    public long getPageNum() {
        return (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    public long getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public void setPageNum(Long pageNum) {
        this.pageNum = pageNum;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isAscending() {
        return !"desc".equalsIgnoreCase(isAsc);
    }
}
