package com.aip.platform.llm.service.impl;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.llm.entity.LlmCallLog;
import com.aip.platform.llm.mapper.LlmCallLogMapper;
import com.aip.platform.llm.service.ILlmCallLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 大模型调用日志服务实现
 */
@Slf4j
@Service
public class LlmCallLogServiceImpl extends ServiceImpl<LlmCallLogMapper, LlmCallLog> implements ILlmCallLogService {

    private final Executor taskExecutor;

    public LlmCallLogServiceImpl(@Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void recordAsync(LlmCallLog entity) {
        try {
            if (entity.getCallTime() == null) {
                entity.setCallTime(LocalDateTime.now());
            }
            if (entity.getUserId() == null) {
                entity.setUserId(SecurityUtils.getUserId());
            }
            // 响应体过长时截断，避免撑爆表
            if (entity.getResponseBody() != null && entity.getResponseBody().length() > 16000) {
                entity.setResponseBody(entity.getResponseBody().substring(0, 16000) + "...[truncated]");
            }
            taskExecutor.execute(() -> {
                try {
                    baseMapper.insert(entity);
                } catch (Exception e) {
                    log.warn("写入大模型调用日志失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交大模型调用日志失败: {}", e.getMessage());
        }
    }

    @Override
    public PageResult<LlmCallLog> selectPage(PageQuery query, LlmCallLog params) {
        LambdaQueryWrapper<LlmCallLog> wrapper = new LambdaQueryWrapper<>();
        if (params != null) {
            if (params.getModelId() != null) {
                wrapper.eq(LlmCallLog::getModelId, params.getModelId());
            }
            if (params.getStatus() != null) {
                wrapper.eq(LlmCallLog::getStatus, params.getStatus());
            }
            if (params.getBizType() != null && !params.getBizType().isEmpty()) {
                wrapper.eq(LlmCallLog::getBizType, params.getBizType());
            }
            if (params.getBizId() != null && !params.getBizId().isEmpty()) {
                wrapper.eq(LlmCallLog::getBizId, params.getBizId());
            }
        }
        wrapper.orderByDesc(LlmCallLog::getCallId);
        IPage<LlmCallLog> page = baseMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<Map<String, Object>> statByBizType() {
        return baseMapper.statByBizType();
    }

    @Override
    public Map<String, Object> statOverview() {
        Map<String, Object> result = new HashMap<>(8);
        result.put("total", baseMapper.selectCount(null));
        result.put("failed", baseMapper.selectCount(
                new LambdaQueryWrapper<LlmCallLog>().eq(LlmCallLog::getStatus, 1)));
        result.put("avgCost", baseMapper.statAvgCost());
        result.put("totalTokens", baseMapper.statTotalTokens());
        return result;
    }

    @Override
    public int cleanBefore(int days) {
        LocalDateTime before = LocalDateTime.now().minusDays(Math.max(days, 1));
        return baseMapper.delete(new LambdaQueryWrapper<LlmCallLog>().lt(LlmCallLog::getCallTime, before));
    }
}
