package com.aip.platform.skill.service;

import com.aip.platform.skill.entity.SkillScript;
import com.aip.platform.skill.entity.SkillScriptLog;
import com.aip.platform.skill.script.ScriptResult;

import java.util.Collection;
import java.util.List;

/**
 * 技能脚本服务
 */
public interface ISkillScriptService {

    /** 触发来源：前端手工运行 */
    String BIZ_MANUAL = "MANUAL";
    /** 触发来源：智能体对话自动调用 */
    String BIZ_AGENT = "AGENT";

    /** 查询某技能下的全部脚本 */
    List<SkillScript> selectBySkillId(Long skillId);

    /** 脚本详情 */
    SkillScript selectDetail(Long scriptId);

    /** 查询多个技能下可用的脚本 */
    List<SkillScript> selectEnabledBySkillIds(List<Long> skillIds);

    /** 新增脚本 */
    int insertScript(SkillScript script);

    /** 修改脚本 */
    int updateScript(SkillScript script);

    /** 删除脚本 */
    int deleteScripts(List<Long> scriptIds);

    /**
     * 执行已保存的脚本，并落一条执行记录
     *
     * @param scriptId   脚本ID
     * @param paramsJson 入参 JSON 字符串
     * @param bizType    触发来源 MANUAL / AGENT
     * @param bizId      触发对象ID
     */
    ScriptResult run(Long scriptId, String paramsJson, String bizType, String bizId);

    /**
     * 在候选脚本中按编码找到脚本并执行，供大模型对话调用
     *
     * @param candidates 候选脚本（通常是该智能体挂载技能下的脚本）
     * @param code       脚本编码
     * @param argsJson   入参 JSON
     * @param bizType    触发来源
     * @param bizId      触发对象ID
     */
    ScriptResult runByCode(Collection<SkillScript> candidates, String code, String argsJson,
                           String bizType, String bizId);

    /** 执行一段未保存的脚本源码（在线调试用），同样记录日志 */
    ScriptResult runInline(String code, String paramsJson, Integer timeoutSec, Long skillId);

    /** 查询某脚本最近的执行记录 */
    List<SkillScriptLog> selectLogs(Long scriptId, Integer limit);

    /**
     * 生成「可用工具」提示词片段，注入系统提示词后大模型才知道有哪些脚本可以调用。
     * 没有可用脚本时返回空字符串。
     */
    String buildToolPrompt(List<Long> skillIds);
}
