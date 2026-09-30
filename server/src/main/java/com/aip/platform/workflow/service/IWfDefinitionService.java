package com.aip.platform.workflow.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.workflow.entity.WfDefinition;

import java.util.List;
import java.util.Map;

/**
 * 工作流定义服务
 */
public interface IWfDefinitionService {

    /**
     * 分页查询流程定义
     *
     * @param query 分页参数
     * @param def   查询条件：defName / defCode(或defKey) / category / status / beginTime / endTime
     * @return 分页结果
     */
    PageResult<WfDefinition> selectPage(PageQuery query, WfDefinition def);

    /**
     * 查询详情（含画布对象 graph）
     *
     * @param defId 定义ID
     * @return 流程定义，不存在返回 null
     */
    WfDefinition selectDetail(Long defId);

    /**
     * 新增流程定义
     *
     * @param def 流程定义（前端传的 defKey 会自动映射到 defCode）
     * @return 影响行数
     */
    int insertDefinition(WfDefinition def);

    /**
     * 修改流程定义
     *
     * @param def 流程定义
     * @return 影响行数
     */
    int updateDefinition(WfDefinition def);

    /**
     * 批量物理删除流程定义
     *
     * @param defIds 定义ID集合
     * @return 影响行数
     */
    int deleteDefinitions(List<Long> defIds);

    /**
     * 发布流程：status=1、published=1、version+1
     *
     * @param defId 定义ID
     * @return 影响行数
     */
    int publish(Long defId);

    /**
     * 执行一次流程
     *
     * @param defId  定义ID
     * @param params 入参
     * @return {instanceId, status, output, costMs}（失败时额外返回 errorMsg）
     */
    Map<String, Object> execute(Long defId, Map<String, Object> params);
}
