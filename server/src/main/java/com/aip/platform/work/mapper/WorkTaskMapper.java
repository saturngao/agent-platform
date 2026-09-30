package com.aip.platform.work.mapper;

import com.aip.platform.work.entity.WorkTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 工作任务数据访问
 *
 * <p>复杂查询统一使用 @Select + &lt;script&gt; 动态 SQL，不引入 XML。</p>
 */
public interface WorkTaskMapper extends BaseMapper<WorkTask> {

    /** 查询列：左连部门取部门名称；del_flag = 0 排除逻辑删除 */
    String TASK_COLUMNS = "SELECT t.*, d.dept_name FROM work_task t "
            + "LEFT JOIN sys_dept d ON t.dept_id = d.dept_id "
            + "WHERE t.del_flag = 0 ";

    /** 动态条件 + 数据权限 + 排序 */
    String TASK_CONDITIONS =
            // 标题模糊（前端别名 taskName 已在 Service 归一化到 taskTitle）
            "<if test=\"q.taskTitle != null and q.taskTitle != ''\">"
                    + " AND t.task_title LIKE concat('%', #{q.taskTitle}, '%')</if>"
            // 状态 / 优先级（统一大写）
            + "<if test=\"q.status != null and q.status != ''\"> AND t.status = #{q.status}</if>"
            + "<if test=\"q.priority != null and q.priority != ''\"> AND t.priority = #{q.priority}</if>"
            // 负责人
            + "<if test=\"q.assigneeId != null\"> AND t.assignee_id = #{q.assigneeId}</if>"
            // 部门
            + "<if test=\"q.deptId != null\"> AND t.dept_id = #{q.deptId}</if>"
            // 父任务
            + "<if test=\"q.parentId != null\"> AND t.parent_id = #{q.parentId}</if>"
            // 时间区间：按 create_time 过滤（beginTime/endTime 均为 yyyy-MM-dd）
            + "<if test=\"q.beginTime != null and q.beginTime != ''\">"
                    + " AND t.create_time &gt;= #{q.beginTime}</if>"
            + "<if test=\"q.endTime != null and q.endTime != ''\">"
                    + " AND t.create_time &lt;= concat(#{q.endTime}, ' 23:59:59')</if>"
            // 数据权限：非超管只能看「自己负责」或「本部门」的任务
            + "<if test=\"!isAdmin\"> AND (t.assignee_id = #{userId}"
                    + "<if test=\"deptId != null\"> OR t.dept_id = #{deptId}</if>)</if>"
            + " ORDER BY t.task_id DESC";

    /**
     * 分页查询任务
     *
     * @param page    分页对象
     * @param q       查询条件
     * @param isAdmin 是否超管（true 时不做数据权限过滤）
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 分页结果
     */
    @Select("<script>" + TASK_COLUMNS + TASK_CONDITIONS + "</script>")
    IPage<WorkTask> selectTaskPage(IPage<WorkTask> page, @Param("q") WorkTask q,
                                   @Param("isAdmin") boolean isAdmin,
                                   @Param("userId") Long userId,
                                   @Param("deptId") Long deptId);

    /**
     * 不分页查询任务（下拉、导出场景）
     *
     * @param q       查询条件
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 任务列表
     */
    @Select("<script>" + TASK_COLUMNS + TASK_CONDITIONS + "</script>")
    List<WorkTask> selectTaskList(@Param("q") WorkTask q,
                                  @Param("isAdmin") boolean isAdmin,
                                  @Param("userId") Long userId,
                                  @Param("deptId") Long deptId);

    /**
     * 任务状态分布统计（看板用）
     *
     * @param isAdmin 是否超管
     * @param userId  当前登录用户ID
     * @param deptId  当前登录用户部门ID
     * @return 形如 [{status: 'DOING', cnt: 3}]
     */
    @Select("<script>SELECT t.status AS status, COUNT(1) AS cnt FROM work_task t WHERE t.del_flag = 0 "
            + "<if test=\"!isAdmin\"> AND (t.assignee_id = #{userId}"
            + "<if test=\"deptId != null\"> OR t.dept_id = #{deptId}</if>)</if>"
            + " GROUP BY t.status</script>")
    List<Map<String, Object>> selectStatusStat(@Param("isAdmin") boolean isAdmin,
                                               @Param("userId") Long userId,
                                               @Param("deptId") Long deptId);
}
