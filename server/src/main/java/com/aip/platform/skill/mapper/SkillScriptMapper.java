package com.aip.platform.skill.mapper;

import com.aip.platform.skill.entity.SkillScript;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 技能脚本数据访问
 */
public interface SkillScriptMapper extends BaseMapper<SkillScript> {

    /** 查询某个技能下的全部脚本 */
    @Select("SELECT s.*, k.skill_name AS skill_name, k.skill_code AS skill_code " +
            "FROM skill_script s LEFT JOIN skill k ON k.skill_id = s.skill_id " +
            "WHERE s.skill_id = #{skillId} AND s.del_flag = 0 " +
            "ORDER BY s.script_id ASC")
    List<SkillScript> selectBySkillId(@Param("skillId") Long skillId);

    /** 查询多个技能下处于正常状态的脚本，供大模型调用 */
    @Select("<script>" +
            "SELECT s.*, k.skill_name AS skill_name, k.skill_code AS skill_code " +
            "FROM skill_script s LEFT JOIN skill k ON k.skill_id = s.skill_id " +
            "WHERE s.del_flag = 0 AND s.status = 0 AND s.skill_id IN " +
            "<foreach collection='skillIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY s.skill_id ASC, s.script_id ASC" +
            "</script>")
    List<SkillScript> selectEnabledBySkillIds(@Param("skillIds") List<Long> skillIds);
}
