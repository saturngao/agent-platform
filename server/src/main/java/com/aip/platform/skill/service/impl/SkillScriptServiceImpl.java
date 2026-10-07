package com.aip.platform.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.SkillScript;
import com.aip.platform.skill.entity.SkillScriptLog;
import com.aip.platform.skill.mapper.SkillScriptLogMapper;
import com.aip.platform.skill.mapper.SkillScriptMapper;
import com.aip.platform.skill.script.PythonScriptExecutor;
import com.aip.platform.skill.script.ScriptResult;
import com.aip.platform.skill.service.ISkillScriptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 技能脚本服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillScriptServiceImpl implements ISkillScriptService {

    private final SkillScriptMapper scriptMapper;
    private final SkillScriptLogMapper logMapper;
    private final PythonScriptExecutor executor;

    // ====================== 查询 ======================

    @Override
    public List<SkillScript> selectBySkillId(Long skillId) {
        if (skillId == null) {
            return List.of();
        }
        return scriptMapper.selectBySkillId(skillId);
    }

    @Override
    public SkillScript selectDetail(Long scriptId) {
        if (scriptId == null) {
            throw new BusinessException("脚本ID不能为空");
        }
        return scriptMapper.selectById(scriptId);
    }

    @Override
    public List<SkillScript> selectEnabledBySkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return scriptMapper.selectEnabledBySkillIds(skillIds);
    }

    // ====================== 增删改 ======================

    @Override
    public int insertScript(SkillScript script) {
        validate(script, true);
        script.setScriptId(null);
        if (script.getStatus() == null) {
            script.setStatus(0);
        }
        if (script.getTimeoutSec() == null || script.getTimeoutSec() <= 0) {
            script.setTimeoutSec(30);
        }
        if (StrUtil.isBlank(script.getLanguage())) {
            script.setLanguage("python");
        }
        return scriptMapper.insert(script);
    }

    @Override
    public int updateScript(SkillScript script) {
        validate(script, false);
        return scriptMapper.updateById(script);
    }

    @Override
    public int deleteScripts(List<Long> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return 0;
        }
        return scriptMapper.deleteBatchIds(scriptIds);
    }

    // ====================== 执行 ======================

    @Override
    public ScriptResult run(Long scriptId, String paramsJson, String bizType, String bizId) {
        SkillScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return ScriptResult.fail("脚本不存在，scriptId=" + scriptId);
        }
        if (script.getStatus() != null && script.getStatus() == 1) {
            return ScriptResult.fail("脚本「" + script.getScriptName() + "」已停用");
        }
        if (!"python".equalsIgnoreCase(StrUtil.trimToEmpty(script.getLanguage()))) {
            return ScriptResult.fail("暂不支持执行 " + script.getLanguage() + " 脚本，当前仅支持 python");
        }
        ScriptResult result = executor.run(script.getContent(), paramsJson, script.getTimeoutSec());
        saveLog(script, paramsJson, bizType, bizId, result);
        return result;
    }

    @Override
    public ScriptResult runByCode(Collection<SkillScript> candidates, String code, String argsJson,
                                  String bizType, String bizId) {
        if (candidates == null || candidates.isEmpty() || StrUtil.isBlank(code)) {
            return ScriptResult.fail("没有可调用的脚本");
        }
        SkillScript target = null;
        for (SkillScript s : candidates) {
            if (code.equals(s.getScriptCode())) {
                target = s;
                break;
            }
        }
        if (target == null) {
            return ScriptResult.fail("未找到编码为「" + code + "」的脚本，只能使用工具清单中列出的脚本编码");
        }
        return run(target.getScriptId(), argsJson, bizType, bizId);
    }

    @Override
    public ScriptResult runInline(String code, String paramsJson, Integer timeoutSec, Long skillId) {
        ScriptResult result = executor.run(code, paramsJson, timeoutSec);
        SkillScript virtual = new SkillScript();
        virtual.setSkillId(skillId);
        virtual.setScriptCode("__inline__");
        saveLog(virtual, paramsJson, BIZ_MANUAL, null, result);
        return result;
    }

    @Override
    public List<SkillScriptLog> selectLogs(Long scriptId, Integer limit) {
        if (scriptId == null) {
            return List.of();
        }
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return logMapper.selectLatest(scriptId, size);
    }

    // ====================== 提示词 ======================

    @Override
    public String buildToolPrompt(List<Long> skillIds) {
        List<SkillScript> scripts = selectEnabledBySkillIds(skillIds);
        if (scripts == null || scripts.isEmpty()) {
            return "";
        }
        if (!executor.isEnabled()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 可用工具（Python 脚本）\n");
        sb.append("下面的工具是真实可执行的 Python 脚本。当任务确实需要真实计算、查询或处理数据时才调用，\n");
        sb.append("能靠推理回答的问题不要调用。\n\n");
        for (SkillScript s : scripts) {
            sb.append("- 工具名：").append(s.getScriptCode()).append("\n");
            sb.append("  用途：").append(StrUtil.isBlank(s.getDescription())
                    ? StrUtil.trimToEmpty(s.getScriptName()) : s.getDescription()).append("\n");
            if (StrUtil.isNotBlank(s.getParamDesc())) {
                sb.append("  参数：").append(s.getParamDesc().trim()).append("\n");
            } else {
                sb.append("  参数：无\n");
            }
        }
        sb.append("\n## 调用方式\n");
        sb.append("需要调用时，整条回复只输出下面这个代码块，语言标记必须写 tool，不要输出任何其它文字：\n\n");
        sb.append("```tool\n");
        sb.append("{\"tool\": \"工具名\", \"args\": { ... }}\n");
        sb.append("```\n\n");
        sb.append("规则：\n");
        sb.append("1. 代码块必须用三个反引号包裹且语言标记为 tool，不要省略反引号；\n");
        sb.append("2. args 必须是 JSON 对象，参数名与用途一一对应；\n");
        sb.append("3. 一次只调用一个工具，收到执行结果后再决定是继续调用还是给出最终答案；\n");
        sb.append("4. 只能使用上面列出的工具名，禁止臆造；\n");
        sb.append("5. 拿到结果后用中文把结论讲清楚，不要原样复读原始输出。\n");
        sb.append("# 工具说明结束\n");
        return sb.toString();
    }

    // ====================== 私有方法 ======================

    private void validate(SkillScript script, boolean isInsert) {
        if (script == null) {
            throw new BusinessException("脚本信息不能为空");
        }
        if (script.getSkillId() == null) {
            throw new BusinessException("所属技能不能为空");
        }
        if (StrUtil.isBlank(script.getScriptCode())) {
            throw new BusinessException("脚本编码不能为空");
        }
        if (StrUtil.isBlank(script.getScriptName())) {
            throw new BusinessException("脚本名称不能为空");
        }
        if (StrUtil.isBlank(script.getContent())) {
            throw new BusinessException("脚本内容不能为空");
        }
        // 同一技能内编码唯一
        LambdaQueryWrapper<SkillScript> wrapper = new LambdaQueryWrapper<SkillScript>()
                .eq(SkillScript::getSkillId, script.getSkillId())
                .eq(SkillScript::getScriptCode, script.getScriptCode());
        if (!isInsert) {
            wrapper.ne(SkillScript::getScriptId, script.getScriptId());
        }
        if (scriptMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该技能下已存在编码为「" + script.getScriptCode() + "」的脚本");
        }
    }

    /** 落执行记录，写库失败不能影响执行结果返回 */
    private void saveLog(SkillScript script, String paramsJson, String bizType, String bizId, ScriptResult result) {
        try {
            SkillScriptLog entity = new SkillScriptLog();
            entity.setScriptId(script.getScriptId() == null ? null : script.getScriptId());
            entity.setSkillId(script.getSkillId());
            entity.setScriptCode(script.getScriptCode());
            entity.setBizType(StrUtil.isBlank(bizType) ? BIZ_MANUAL : bizType);
            entity.setBizId(bizId);
            entity.setInputParams(truncate(paramsJson, 4000));
            entity.setExitCode(result.getExitCode());
            entity.setOutputText(truncate(result.getStdout(), 8000));
            entity.setErrorText(truncate(result.getStderr(), 8000));
            entity.setCostMs(result.getCostMs());
            entity.setStatus(result.isSuccess() ? 0 : 1);
            entity.setCreateBy(SecurityUtils.getUsername());
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("保存脚本执行记录失败: {}", e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 供工具清单去重使用 */
    private List<SkillScript> distinct(List<SkillScript> scripts) {
        List<SkillScript> result = new ArrayList<>();
        if (scripts == null) {
            return result;
        }
        for (SkillScript s : scripts) {
            if (s != null) {
                result.add(s);
            }
        }
        return result;
    }
}
