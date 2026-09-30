package com.aip.platform.skill.mapper;

import com.aip.platform.skill.entity.Skill;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 技能数据访问
 */
public interface SkillMapper extends BaseMapper<Skill> {

    /**
     * 分页查询技能，支持名称/编码模糊、分类、来源、状态过滤。
     * 同时统计每个技能被多少智能体/工作流挂载。
     */
    @Select("<script>" +
            "SELECT s.*, " +
            "  (SELECT COUNT(*) FROM skill_binding b WHERE b.skill_id = s.skill_id AND b.biz_type = 'AGENT') AS agent_count, " +
            "  (SELECT COUNT(*) FROM skill_binding b WHERE b.skill_id = s.skill_id AND b.biz_type = 'WORKFLOW') AS workflow_count " +
            "FROM skill s " +
            "<where>" +
            "  s.del_flag = 0 " +
            "  <if test=\"q.skillName != null and q.skillName != ''\">" +
            "    AND (s.skill_name LIKE concat('%', #{q.skillName}, '%') OR s.skill_code LIKE concat('%', #{q.skillName}, '%'))" +
            "  </if>" +
            "  <if test=\"q.category != null and q.category != ''\">AND s.category = #{q.category}</if>" +
            "  <if test=\"q.source != null and q.source != ''\">AND s.source = #{q.source}</if>" +
            "  <if test=\"q.status != null\">AND s.status = #{q.status}</if>" +
            "</where>" +
            "ORDER BY s.skill_id DESC" +
            "</script>")
    IPage<Skill> selectSkillPage(IPage<Skill> page, @Param("q") Skill q);

    /**
     * 查询挂载在某个业务对象上的技能列表（按技能ID正序，保证注入顺序稳定）
     */
    @Select("SELECT s.* FROM skill s " +
            "INNER JOIN skill_binding b ON b.skill_id = s.skill_id " +
            "WHERE b.biz_type = #{bizType} AND b.biz_id = #{bizId} AND s.del_flag = 0 AND s.status = 0 " +
            "ORDER BY s.skill_id ASC")
    List<Skill> selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
