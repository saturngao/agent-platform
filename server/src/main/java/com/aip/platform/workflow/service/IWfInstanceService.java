package com.aip.platform.workflow.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.workflow.dto.WfInstanceQuery;
import com.aip.platform.workflow.entity.WfInstance;
import com.aip.platform.workflow.entity.WfNodeRun;

import java.util.List;
import java.util.Map;

/**
 * 工作流运行实例服务
 */
public interface IWfInstanceService {

    /**
     * 分页查询运行实例
     *
     * @param query 分页参数
     * @param q     查询条件：defId / defName / status / beginTime / endTime
     * @return 分页结果
     */
    PageResult<WfInstance> selectPage(PageQuery query, WfInstanceQuery q);

    /**
     * 查询实例详情
     *
     * @param instanceId 实例ID
     * @return 实例，不存在返回 null
     */
    WfInstance selectDetail(Long instanceId);

    /**
     * 查询实例详情 + 节点执行明细（前端时间线用）
     *
     * @param instanceId 实例ID
     * @return {instance:{...}, nodeRuns:[...]}
     */
    Map<String, Object> selectFullDetail(Long instanceId);

    /**
     * 查询节点执行明细
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    List<WfNodeRun> selectNodeRuns(Long instanceId);

    /**
     * 重跑一次（会生成一条新的实例记录，保留历史轨迹）
     *
     * @param instanceId 原实例ID
     * @return {instanceId, status, output, costMs}
     */
    Map<String, Object> retry(Long instanceId);

    /**
     * 停止实例（SCHEDULE / 异步场景预留，同步执行中的实例不受影响）
     *
     * @param instanceId 实例ID
     * @return 影响行数
     */
    int stop(Long instanceId);

    /**
     * 批量物理删除实例及其节点记录
     *
     * @param instanceIds 实例ID集合
     * @return 影响行数
     */
    int deleteInstances(List<Long> instanceIds);
}
