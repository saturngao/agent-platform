package com.aip.platform.skill.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.skill.entity.Skill;

import java.util.List;
import java.util.Map;

/**
 * 技能服务
 */
public interface ISkillService {

    /** 分页查询 */
    PageResult<Skill> selectPage(PageQuery query, Skill q);

    /** 详情 */
    Skill selectDetail(Long skillId);

    /** 按编码查询 */
    Skill selectByCode(String skillCode);

    /** 新增 */
    int insertSkill(Skill skill);

    /** 修改 */
    int updateSkill(Skill skill);

    /** 逻辑删除 */
    int deleteSkills(List<Long> skillIds);

    /** 启停 */
    int changeStatus(Long skillId, Integer status);

    /** 下拉选项（只返回正常状态） */
    List<Map<String, Object>> selectOptions();

    /** 查询某业务对象挂载的技能 */
    List<Skill> selectByBiz(String bizType, Long bizId);

    /** 查询某业务对象已挂载的技能ID */
    List<Long> selectSkillIdsByBiz(String bizType, Long bizId);

    /**
     * 重建绑定关系（先清后插）
     *
     * @param bizType  AGENT / WORKFLOW
     * @param bizId    业务对象ID
     * @param skillIds 技能ID列表，为空表示解除全部
     */
    void bindSkills(String bizType, Long bizId, List<Long> skillIds);

    /**
     * 把某业务对象挂载的全部技能拼装成可注入系统提示词的片段。
     * 没有挂载任何技能时返回空字符串，调用方直接拼接即可。
     */
    String buildSkillPrompt(String bizType, Long bizId);
}
