package com.aip.platform.workflow.mapper;

import com.aip.platform.workflow.entity.WfDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流定义数据访问
 *
 * <p>列表查询不返回 graph_json（MEDIUMTEXT），避免列表接口传输大量无用数据。</p>
 */
public interface WfDefinitionMapper extends BaseMapper<WfDefinition> {

    /**
     * 分页查询流程定义
     *
     * <p>IPage 只作为入参，由 MyBatis-Plus 分页插件完成 count 查询与 LIMIT 追加，
     * 返回值用 List 保证结果映射稳定。</p>
     *
     * @param page 分页参数
     * @param q    查询条件：defName / defCode / category / status / beginTime / endTime
     * @return 当前页数据
     */
    @Select("<script>"
            + "SELECT def_id, def_code, def_name, category, icon, description, version, status, published, dept_id, "
            + "       create_by, create_time, update_by, update_time "
            + "FROM wf_definition "
            + "<where>"
            + "  <if test='q.defName != null and q.defName != \"\"'>AND def_name LIKE concat('%', #{q.defName}, '%')</if>"
            + "  <if test='q.status != null'>AND status = #{q.status}</if>"
            + "  <if test='q.category != null and q.category != \"\"'>AND category = #{q.category}</if>"
            + "  <if test='q.defCode != null and q.defCode != \"\"'>AND def_code = #{q.defCode}</if>"
            + "  <if test='q.deptId != null'>AND dept_id = #{q.deptId}</if>"
            + "  <if test='q.beginTime != null and q.beginTime != \"\"'>AND create_time &gt;= #{q.beginTime}</if>"
            + "  <if test='q.endTime != null and q.endTime != \"\"'>AND create_time &lt;= #{q.endTime}</if>"
            + "</where>"
            + " ORDER BY def_id DESC"
            + "</script>")
    List<WfDefinition> selectDefPage(IPage<WfDefinition> page, @Param("q") WfDefinition q);
}
