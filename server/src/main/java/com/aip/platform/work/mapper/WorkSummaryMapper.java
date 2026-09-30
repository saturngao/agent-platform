package com.aip.platform.work.mapper;

import com.aip.platform.work.entity.WorkSummary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 工作总结数据访问
 *
 * <p>work_summary 不做逻辑删除，这里只提供分页查询（动态 SQL 内联，不使用 XML）。</p>
 */
public interface WorkSummaryMapper extends BaseMapper<WorkSummary> {

    /** 查询列与动态条件 */
    String SUMMARY_SQL = "SELECT * FROM work_summary "
            + "<where>"
            + "<if test=\"q.scopeType != null and q.scopeType != ''\"> AND scope_type = #{q.scopeType}</if>"
            + "<if test=\"q.summaryTitle != null and q.summaryTitle != ''\">"
                    + " AND summary_title LIKE concat('%', #{q.summaryTitle}, '%')</if>"
            + "<if test=\"q.status != null and q.status != ''\"> AND status = #{q.status}</if>"
            // 数据权限：非超管只能看自己生成的总结
            + "<if test=\"!isAdmin\"> AND user_id = #{userId}</if>"
            + "</where>"
            + " ORDER BY summary_id DESC";

    /**
     * 分页查询总结
     *
     * @param page    分页对象
     * @param q       查询条件
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @return 分页结果
     */
    @Select("<script>" + SUMMARY_SQL + "</script>")
    IPage<WorkSummary> selectSummaryPage(IPage<WorkSummary> page, @Param("q") WorkSummary q,
                                         @Param("isAdmin") boolean isAdmin,
                                         @Param("userId") Long userId);

    /**
     * 不分页查询总结
     *
     * @param q       查询条件
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @return 总结列表
     */
    @Select("<script>" + SUMMARY_SQL + "</script>")
    List<WorkSummary> selectSummaryList(@Param("q") WorkSummary q,
                                        @Param("isAdmin") boolean isAdmin,
                                        @Param("userId") Long userId);
}
