package com.aip.platform.workflow.mapper;

import com.aip.platform.workflow.dto.WfInstanceQuery;
import com.aip.platform.workflow.entity.WfInstance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流运行实例数据访问
 */
public interface WfInstanceMapper extends BaseMapper<WfInstance> {

    /**
     * 分页查询运行实例（关联流程定义取名称）
     *
     * <p>IPage 只作为入参，由 MyBatis-Plus 分页插件完成 count 查询与 LIMIT 追加。</p>
     *
     * @param page 分页参数
     * @param q    查询条件：defId / defName / status / beginTime / endTime
     * @return 当前页数据
     */
    @Select("<script>"
            + "SELECT i.instance_id, i.def_id, i.def_code, i.biz_no, i.status, i.error_msg, i.trigger_type, "
            + "       i.cost_ms, i.token_total, i.start_time, i.end_time, i.user_id, i.username, "
            + "       d.def_name AS defName "
            + "FROM wf_instance i LEFT JOIN wf_definition d ON i.def_id = d.def_id "
            + "<where>"
            + "  <if test='q.defId != null'>AND i.def_id = #{q.defId}</if>"
            + "  <if test='q.defName != null and q.defName != \"\"'>AND d.def_name LIKE concat('%', #{q.defName}, '%')</if>"
            + "  <if test='q.status != null and q.status != \"\"'>AND i.status = #{q.status}</if>"
            + "  <if test='q.beginTime != null and q.beginTime != \"\"'>AND i.start_time &gt;= #{q.beginTime}</if>"
            + "  <if test='q.endTime != null and q.endTime != \"\"'>AND i.start_time &lt;= #{q.endTime}</if>"
            + "</where>"
            + " ORDER BY i.instance_id DESC"
            + "</script>")
    List<WfInstance> selectInstancePage(IPage<WfInstance> page, @Param("q") WfInstanceQuery q);
}
