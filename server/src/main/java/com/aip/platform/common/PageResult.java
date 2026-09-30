package com.aip.platform.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long totalPages;

    /** 数据列表 */
    private List<T> rows;

    public PageResult() {
        this.rows = Collections.emptyList();
    }

    public PageResult(List<T> rows, long total, long pageNum, long pageSize) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (total + pageSize - 1) / pageSize : 0;
    }

    public static <T> PageResult<T> of(List<T> rows, long total, long pageNum, long pageSize) {
        return new PageResult<>(rows, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }

    /** 内存分页（用于树形结构或已全量取数的场景） */
    public static <T> PageResult<T> page(List<T> all, long pageNum, long pageSize) {
        if (all == null || all.isEmpty()) {
            return empty(pageNum, pageSize);
        }
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(pageNum * pageSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), pageNum, pageSize);
    }

    public <U> PageResult<U> map(Function<T, U> mapper) {
        List<U> newRows = this.rows.stream().map(mapper).collect(java.util.stream.Collectors.toList());
        return new PageResult<>(newRows, this.total, this.pageNum, this.pageSize);
    }
}
