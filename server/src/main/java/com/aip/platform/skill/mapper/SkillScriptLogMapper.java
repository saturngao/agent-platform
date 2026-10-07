package com.aip.platform.skill.mapper;

import com.aip.platform.skill.entity.SkillScriptLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 脚本执行记录数据访问
 */
public interface SkillScriptLogMapper extends BaseMapper<SkillScriptLog> {

    /** 查询某个脚本最近的执行记录 */
    @Select("SELECT * FROM skill_script_log " +
            "WHERE script_id = #{scriptId} " +
            "ORDER BY log_id DESC LIMIT #{limit}")
    List<SkillScriptLog> selectLatest(@Param("scriptId") Long scriptId, @Param("limit") Integer limit);
}
