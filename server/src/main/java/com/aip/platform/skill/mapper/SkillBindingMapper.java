package com.aip.platform.skill.mapper;

import com.aip.platform.skill.entity.SkillBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 技能绑定关系数据访问
 */
public interface SkillBindingMapper extends BaseMapper<SkillBinding> {

    /** 查询某业务对象已绑定的技能ID */
    @Select("SELECT skill_id FROM skill_binding WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    List<Long> selectSkillIdsByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 批量新增绑定（已存在则忽略，依赖唯一索引） */
    @Insert("<script>" +
            "INSERT IGNORE INTO skill_binding (skill_id, biz_type, biz_id, create_by, create_time) VALUES " +
            "<foreach collection=\"skillIds\" item=\"sid\" separator=\",\">" +
            "  (#{sid}, #{bizType}, #{bizId}, #{createBy}, NOW())" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("skillIds") List<Long> skillIds,
                    @Param("bizType") String bizType,
                    @Param("bizId") Long bizId,
                    @Param("createBy") String createBy);

    /** 清空某业务对象的全部绑定 */
    @Delete("DELETE FROM skill_binding WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 删除某个技能的所有绑定关系（技能被删除时调用） */
    @Delete("DELETE FROM skill_binding WHERE skill_id = #{skillId}")
    int deleteBySkillId(@Param("skillId") Long skillId);
}
