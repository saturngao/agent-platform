package com.aip.platform.llm.mapper;

import com.aip.platform.llm.entity.LlmCallLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 大模型调用日志 Mapper
 */
@Mapper
public interface LlmCallLogMapper extends BaseMapper<LlmCallLog> {

    @Select("SELECT biz_type AS bizType, COUNT(1) AS count, ROUND(AVG(cost_ms),0) AS avgCost "
            + "FROM llm_call_log GROUP BY biz_type ORDER BY count DESC")
    List<Map<String, Object>> statByBizType();

    @Select("SELECT ROUND(IFNULL(AVG(cost_ms),0),0) FROM llm_call_log WHERE status = 0")
    Long statAvgCost();

    @Select("SELECT IFNULL(SUM(total_tokens),0) FROM llm_call_log WHERE status = 0")
    Long statTotalTokens();
}
