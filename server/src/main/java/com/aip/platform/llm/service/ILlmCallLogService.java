package com.aip.platform.llm.service;

import com.aip.platform.llm.entity.LlmCallLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.PageQuery;

import java.util.List;
import java.util.Map;

/**
 * 大模型调用日志服务
 */
public interface ILlmCallLogService extends IService<LlmCallLog> {

    /**
     * 异步记录一次调用结果
     */
    void recordAsync(LlmCallLog log);

    /**
     * 分页查询调用日志
     */
    PageResult<LlmCallLog> selectPage(PageQuery query, LlmCallLog params);

    /**
     * 统计：按业务类型分组的调用次数
     */
    List<Map<String, Object>> statByBizType();

    /**
     * 统计：调用总数 / 失败数 / 平均耗时
     */
    Map<String, Object> statOverview();

    /**
     * 清空指定天数之前的日志
     */
    int cleanBefore(int days);
}
