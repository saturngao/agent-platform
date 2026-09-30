package com.aip.platform.work.mapper;

import com.aip.platform.work.entity.WorkLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 工作日志数据访问
 *
 * <p>复杂查询统一使用 @Select + &lt;script&gt; 动态 SQL，不引入 XML。</p>
 */
public interface WorkLogMapper extends BaseMapper<WorkLog> {

    /** 查询列：左连任务与部门；排除逻辑删除与 DELETED 状态 */
    String LOG_COLUMNS = "SELECT l.*, t.task_title, d.dept_name FROM work_log l "
            + "LEFT JOIN work_task t ON l.task_id = t.task_id "
            + "LEFT JOIN sys_dept d ON l.dept_id = d.dept_id "
            + "WHERE l.del_flag = 0 AND (l.status IS NULL OR l.status &lt;&gt; 'DELETED') ";

    /** 动态条件 + 数据权限 + 排序 */
    String LOG_CONDITIONS =
            "<if test=\"q.taskId != null\"> AND l.task_id = #{q.taskId}</if>"
            + "<if test=\"q.userId != null\"> AND l.user_id = #{q.userId}</if>"
            + "<if test=\"q.deptId != null\"> AND l.dept_id = #{q.deptId}</if>"
            // 指定某一天
            + "<if test=\"q.workDate != null\"> AND l.work_date = #{q.workDate}</if>"
            // 日志类型
            + "<if test=\"q.logType != null and q.logType != ''\"> AND l.log_type = #{q.logType}</if>"
            // 关键字：正文 / 标题 / 成果 / 阻塞
            + "<if test=\"q.keyword != null and q.keyword != ''\"> AND ("
                    + "l.content LIKE concat('%', #{q.keyword}, '%')"
                    + " OR l.log_title LIKE concat('%', #{q.keyword}, '%')"
                    + " OR l.result LIKE concat('%', #{q.keyword}, '%')"
                    + " OR l.blockers LIKE concat('%', #{q.keyword}, '%'))</if>"
            // 工作日期区间
            + "<if test=\"q.beginTime != null and q.beginTime != ''\">"
                    + " AND l.work_date &gt;= #{q.beginTime}</if>"
            + "<if test=\"q.endTime != null and q.endTime != ''\">"
                    + " AND l.work_date &lt;= #{q.endTime}</if>"
            // 数据权限：非超管只能看自己的日志或本部门的日志
            + "<if test=\"!isAdmin\"> AND (l.user_id = #{userId}"
                    + "<if test=\"deptId != null\"> OR l.dept_id = #{deptId}</if>)</if>"
            + " ORDER BY l.work_date DESC, l.log_id DESC";

    /**
     * 分页查询日志
     *
     * @param page    分页对象
     * @param q       查询条件
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 分页结果
     */
    @Select("<script>" + LOG_COLUMNS + LOG_CONDITIONS + "</script>")
    IPage<WorkLog> selectLogPage(IPage<WorkLog> page, @Param("q") WorkLog q,
                                 @Param("isAdmin") boolean isAdmin,
                                 @Param("userId") Long userId,
                                 @Param("deptId") Long deptId);

    /**
     * 不分页查询日志
     *
     * @param q       查询条件
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 日志列表
     */
    @Select("<script>" + LOG_COLUMNS + LOG_CONDITIONS + "</script>")
    List<WorkLog> selectLogList(@Param("q") WorkLog q,
                                @Param("isAdmin") boolean isAdmin,
                                @Param("userId") Long userId,
                                @Param("deptId") Long deptId);

    /**
     * 按统计周期取日志（AI 总结原始数据）
     *
     * <p>userIds 为空时不加人员条件（由 deptId 或全量兜底），按工作日期升序返回。</p>
     *
     * @param begin   起始日期 yyyy-MM-dd
     * @param end     结束日期 yyyy-MM-dd
     * @param userIds 人员ID集合，为空表示不限
     * @param deptId  部门ID，为空表示不限
     * @return 日志列表
     */
    @Select("<script>SELECT l.* FROM work_log l "
            + "WHERE l.del_flag = 0 AND (l.status IS NULL OR l.status &lt;&gt; 'DELETED') "
            + "<if test=\"begin != null and begin != ''\"> AND l.work_date &gt;= #{begin}</if>"
            + "<if test=\"end != null and end != ''\"> AND l.work_date &lt;= #{end}</if>"
            + "<if test=\"deptId != null\"> AND l.dept_id = #{deptId}</if>"
            + "<if test=\"userIds != null and userIds.size() &gt; 0\"> AND l.user_id IN "
                    + "<foreach collection=\"userIds\" item=\"uid\" open=\"(\" separator=\",\" close=\")\">"
                    + "#{uid}</foreach></if>"
            + " ORDER BY l.work_date ASC, l.log_id ASC</script>")
    List<WorkLog> selectForSummary(@Param("begin") String begin,
                                   @Param("end") String end,
                                   @Param("userIds") List<Long> userIds,
                                   @Param("deptId") Long deptId);

    /**
     * 最近 N 天日志条数趋势
     *
     * @param begin   起始日期 yyyy-MM-dd
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 形如 [{workDate: '2026-09-25', cnt: 4}]
     */
    @Select("<script>SELECT l.work_date AS workDate, COUNT(1) AS cnt FROM work_log l "
            + "WHERE l.del_flag = 0 AND (l.status IS NULL OR l.status &lt;&gt; 'DELETED') "
            + "AND l.work_date &gt;= #{begin} "
            + "<if test=\"!isAdmin\"> AND (l.user_id = #{userId}"
                    + "<if test=\"deptId != null\"> OR l.dept_id = #{deptId}</if>)</if>"
            + " GROUP BY l.work_date ORDER BY l.work_date ASC</script>")
    List<Map<String, Object>> selectDateTrend(@Param("begin") String begin,
                                              @Param("isAdmin") boolean isAdmin,
                                              @Param("userId") Long userId,
                                              @Param("deptId") Long deptId);
}
