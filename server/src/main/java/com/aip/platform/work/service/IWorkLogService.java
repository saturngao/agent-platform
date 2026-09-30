package com.aip.platform.work.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.work.entity.WorkLog;

import java.util.List;
import java.util.Map;

/**
 * 工作日志业务接口
 */
public interface IWorkLogService {

    /**
     * 分页查询日志（自动套数据权限）
     *
     * @param query 分页参数
     * @param log   查询条件
     * @return 分页结果
     */
    PageResult<WorkLog> selectPage(PageQuery query, WorkLog log);

    /**
     * 日志详情
     *
     * @param logId 日志ID
     * @return 日志
     */
    WorkLog selectById(Long logId);

    /**
     * 新增日志（自动补齐 userId / username / deptId）
     *
     * @param log 日志
     * @return 影响行数
     */
    int insertLog(WorkLog log);

    /**
     * 修改日志
     *
     * @param log 日志
     * @return 影响行数
     */
    int updateLog(WorkLog log);

    /**
     * 批量删除（逻辑删除）
     *
     * @param logIds 日志ID集合
     * @return 影响行数
     */
    int deleteLogs(List<Long> logIds);

    /**
     * 我的日志（强制 user_id = 当前登录人）
     *
     * @param query 分页参数
     * @param log   查询条件
     * @return 分页结果
     */
    PageResult<WorkLog> selectMyPage(PageQuery query, WorkLog log);

    /**
     * 按工作日期区间查询
     *
     * @param begin  起始日期 yyyy-MM-dd
     * @param end    结束日期 yyyy-MM-dd
     * @param userId 用户ID，为空表示当前登录人
     * @return 日志列表
     */
    List<WorkLog> selectByRange(String begin, String end, Long userId);

    /**
     * 最近 N 天日志条数趋势（缺失日期补 0）
     *
     * @param days 天数，如 7 表示最近 7 天
     * @return 形如 [{workDate: '2026-09-25', cnt: 4}]
     */
    List<Map<String, Object>> selectDateTrend(int days);
}
