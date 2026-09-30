package com.aip.platform.work.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.work.entity.WorkTask;

import java.util.List;
import java.util.Map;

/**
 * 工作任务业务接口
 */
public interface IWorkTaskService {

    /**
     * 分页查询任务（自动套数据权限）
     *
     * @param query 分页参数
     * @param task  查询条件
     * @return 分页结果
     */
    PageResult<WorkTask> selectPage(PageQuery query, WorkTask task);

    /**
     * 任务详情
     *
     * @param taskId 任务ID
     * @return 任务
     */
    WorkTask selectById(Long taskId);

    /**
     * 新增任务（创建人/指派人 = 当前登录人，状态默认 TODO，进度默认 0）
     *
     * @param task 任务
     * @return 影响行数
     */
    int insertTask(WorkTask task);

    /**
     * 修改任务
     *
     * @param task 任务
     * @return 影响行数
     */
    int updateTask(WorkTask task);

    /**
     * 批量删除（逻辑删除）
     *
     * @param taskIds 任务ID集合
     * @return 影响行数
     */
    int deleteTasks(List<Long> taskIds);

    /**
     * 修改任务状态
     *
     * @param taskId 任务ID
     * @param status 状态 TODO / DOING / REVIEW / DONE / CANCELLED
     * @return 影响行数
     */
    int updateStatus(Long taskId, String status);

    /**
     * 修改任务进度
     *
     * @param taskId   任务ID
     * @param progress 进度 0~100
     * @return 影响行数
     */
    int updateProgress(Long taskId, Integer progress);

    /**
     * 任务状态分布统计（看板用）
     *
     * @return 形如 {total: 10, TODO: 3, DOING: 2, ...}
     */
    Map<String, Object> selectStatusStat();
}
