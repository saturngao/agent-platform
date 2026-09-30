package com.aip.platform.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.workflow.dto.WfInstanceQuery;
import com.aip.platform.workflow.engine.WorkflowEngine;
import com.aip.platform.workflow.entity.WfDefinition;
import com.aip.platform.workflow.entity.WfInstance;
import com.aip.platform.workflow.entity.WfNodeRun;
import com.aip.platform.workflow.mapper.WfDefinitionMapper;
import com.aip.platform.workflow.mapper.WfInstanceMapper;
import com.aip.platform.workflow.mapper.WfNodeRunMapper;
import com.aip.platform.workflow.service.IWfInstanceService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流运行实例服务实现
 */
@Slf4j
@Service
public class WfInstanceServiceImpl extends ServiceImpl<WfInstanceMapper, WfInstance>
        implements IWfInstanceService {

    private final ObjectMapper objectMapper;

    private final WorkflowEngine workflowEngine;

    private final WfDefinitionMapper definitionMapper;

    private final WfNodeRunMapper nodeRunMapper;

    public WfInstanceServiceImpl(ObjectMapper objectMapper,
                                 WorkflowEngine workflowEngine,
                                 WfDefinitionMapper definitionMapper,
                                 WfNodeRunMapper nodeRunMapper) {
        this.objectMapper = objectMapper;
        this.workflowEngine = workflowEngine;
        this.definitionMapper = definitionMapper;
        this.nodeRunMapper = nodeRunMapper;
    }

    @Override
    public PageResult<WfInstance> selectPage(PageQuery query, WfInstanceQuery q) {
        WfInstanceQuery condition = q == null ? new WfInstanceQuery() : q;
        normalizeTime(condition);
        IPage<WfInstance> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<WfInstance> records = baseMapper.selectInstancePage(page, condition);
        // 分页插件只负责 count 与 LIMIT，记录回写由这里完成
        page.setRecords(records == null ? Collections.emptyList() : records);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WfInstance selectDetail(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        return baseMapper.selectById(instanceId);
    }

    @Override
    public Map<String, Object> selectFullDetail(Long instanceId) {
        WfInstance instance = selectDetail(instanceId);
        if (instance == null) {
            throw new BusinessException("运行实例不存在，instanceId=" + instanceId);
        }
        Map<String, Object> detail = new LinkedHashMap<>(2);
        detail.put("instance", instance);
        detail.put("nodeRuns", selectNodeRuns(instanceId));
        return detail;
    }

    @Override
    public List<WfNodeRun> selectNodeRuns(Long instanceId) {
        if (instanceId == null) {
            return Collections.emptyList();
        }
        return nodeRunMapper.selectByInstanceId(instanceId);
    }

    /**
     * 重跑：不允许重复执行 RUNNING 状态的实例。
     *
     * <p>会生成一条新的实例记录（保留历史轨迹），入参沿用原实例的 input_data。</p>
     */
    @Override
    public Map<String, Object> retry(Long instanceId) {
        if (instanceId == null) {
            throw new BusinessException("实例ID不能为空");
        }
        WfInstance old = baseMapper.selectById(instanceId);
        if (old == null) {
            throw new BusinessException("运行实例不存在，instanceId=" + instanceId);
        }
        if (WfInstance.STATUS_RUNNING.equals(old.getStatus())) {
            throw new BusinessException("实例正在运行中，不允许重复执行");
        }
        WfDefinition def = definitionMapper.selectById(old.getDefId());
        if (def == null) {
            throw new BusinessException("原流程定义已被删除，无法重试");
        }
        Map<String, Object> params = parseInput(old.getInputData());
        String triggerType = StrUtil.isBlank(old.getTriggerType())
                ? WfInstance.TRIGGER_MANUAL : old.getTriggerType();
        WfInstance instance = workflowEngine.run(def, params, triggerType);
        Map<String, Object> result = new LinkedHashMap<>(5);
        result.put("instanceId", instance.getInstanceId());
        result.put("status", instance.getStatus());
        result.put("costMs", instance.getCostMs());
        if (instance.getErrorMsg() != null) {
            result.put("errorMsg", instance.getErrorMsg());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int stop(Long instanceId) {
        if (instanceId == null) {
            throw new BusinessException("实例ID不能为空");
        }
        WfInstance instance = baseMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("运行实例不存在，instanceId=" + instanceId);
        }
        if (!WfInstance.STATUS_RUNNING.equals(instance.getStatus())) {
            throw new BusinessException("仅运行中的实例可以停止");
        }
        WfInstance update = new WfInstance();
        update.setInstanceId(instanceId);
        update.setStatus(WfInstance.STATUS_STOPPED);
        update.setEndTime(LocalDateTime.now());
        return baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteInstances(List<Long> instanceIds) {
        if (CollUtil.isEmpty(instanceIds)) {
            throw new BusinessException("请选择要删除的运行实例");
        }
        nodeRunMapper.deleteByInstanceIds(instanceIds);
        return baseMapper.deleteByIds(instanceIds);
    }

    // ============================ 内部方法 ============================

    /** 时间条件补全：只传日期时补成当天的起止时刻，避免查不到当天数据 */
    private void normalizeTime(WfInstanceQuery query) {
        if (query == null) {
            return;
        }
        if (StrUtil.isNotBlank(query.getBeginTime())) {
            String begin = query.getBeginTime().trim();
            query.setBeginTime(begin.length() == 10 ? begin + " 00:00:00" : begin);
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            String end = query.getEndTime().trim();
            query.setEndTime(end.length() == 10 ? end + " 23:59:59" : end);
        }
    }

    /** 还原入参 JSON */
    private Map<String, Object> parseInput(String inputData) {
        if (StrUtil.isBlank(inputData)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed =
                    objectMapper.readValue(inputData, new TypeReference<Map<String, Object>>() {
                    });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            log.warn("实例入参解析失败，按空入参重试：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
