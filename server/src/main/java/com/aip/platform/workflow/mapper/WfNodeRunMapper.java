package com.aip.platform.workflow.mapper;

import com.aip.platform.workflow.entity.WfNodeRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流节点运行记录数据访问
 */
public interface WfNodeRunMapper extends BaseMapper<WfNodeRun> {

    /**
     * 查询某实例的节点执行明细（按执行顺序升序，供前端时间线展示）
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    @Select("SELECT run_id, instance_id, node_id, node_name, node_type, input_data, output_data, branch_taken, "
            + "       status, error_msg, model_id, tokens, cost_ms, seq_no, start_time, end_time "
            + "FROM wf_node_run WHERE instance_id = #{instanceId} ORDER BY seq_no ASC, run_id ASC")
    List<WfNodeRun> selectByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 按实例ID批量物理删除节点运行记录
     *
     * @param instanceIds 实例ID集合
     * @return 删除条数
     */
    @Delete("<script>"
            + "DELETE FROM wf_node_run WHERE instance_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteByInstanceIds(@Param("ids") List<Long> instanceIds);
}
