package com.aip.platform.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.entity.SkillScript;
import com.aip.platform.skill.entity.SkillScriptLog;
import com.aip.platform.skill.file.SkillFileService;
import com.aip.platform.skill.mapper.SkillMapper;
import com.aip.platform.skill.mapper.SkillScriptLogMapper;
import com.aip.platform.skill.mapper.SkillScriptMapper;
import com.aip.platform.skill.script.PythonScriptExecutor;
import com.aip.platform.skill.script.ScriptResult;
import com.aip.platform.skill.service.ISkillScriptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 技能脚本服务实现
 *
 * <p>脚本以文件形式存放在技能目录（默认 server/data/skills/&lt;skillCode&gt;/scripts/）下，
 * 数据库里只保存脚本的元信息（编码、用途、参数说明、超时）与文件相对路径，源码本身不入库。
 * 早期把源码存进数据库的脚本仍可读取执行，首次同步目录时会自动迁移成文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillScriptServiceImpl implements ISkillScriptService {

    private final SkillScriptMapper scriptMapper;
    private final SkillScriptLogMapper logMapper;
    private final SkillMapper skillMapper;
    private final PythonScriptExecutor executor;
    private final SkillFileService fileService;

    // ====================== 查询 ======================

    @Override
    public List<SkillScript> selectBySkillId(Long skillId) {
        if (skillId == null) {
            return List.of();
        }
        List<SkillScript> list = scriptMapper.selectBySkillId(skillId);
        markFileExists(list);
        return list;
    }

    @Override
    public SkillScript selectDetail(Long scriptId) {
        if (scriptId == null) {
            throw new BusinessException("脚本ID不能为空");
        }
        SkillScript script = scriptMapper.selectById(scriptId);
        if (script != null) {
            markFileExists(List.of(script));
        }
        return script;
    }

    @Override
    public List<SkillScript> selectEnabledBySkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        List<SkillScript> list = scriptMapper.selectEnabledBySkillIds(skillIds);
        markFileExists(list);
        return list;
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

        String skillCode = resolveSkillCode(script.getSkillId());
        // 未指定路径时按约定落到 scripts/<code>.py
        String relPath = StrUtil.isBlank(script.getScriptPath())
                ? defaultScriptPath(script.getScriptCode())
                : normalizeRelPath(script.getScriptPath());
        relPath = uniqueRelPath(skillCode, relPath, null);
        script.setScriptPath(relPath);

        // 源码写入技能目录
        String source = script.getSource() != null ? script.getSource() : script.getContent();
        if (StrUtil.isBlank(source)) {
            source = defaultScriptTemplate(script.getScriptCode(), script.getDescription());
        }
        fileService.ensureDir(skillCode);
        fileService.write(skillCode, relPath, source);

        // 源码不入库，只留元信息与路径
        script.setContent(null);
        script.setSource(null);
        return scriptMapper.insert(script);
    }

    @Override
    public int updateScript(SkillScript script) {
        validate(script, false);
        SkillScript db = scriptMapper.selectById(script.getScriptId());
        if (db == null) {
            throw new BusinessException("脚本不存在");
        }
        String skillCode = resolveSkillCode(db.getSkillId());
        // 技能编码在编辑时被改过的话，用库里的旧值定位目录
        String relPath = StrUtil.isBlank(script.getScriptPath()) ? db.getScriptPath() : script.getScriptPath();
        if (StrUtil.isNotBlank(relPath)) {
            relPath = normalizeRelPath(relPath);
            if (!relPath.equals(db.getScriptPath())) {
                relPath = uniqueRelPath(skillCode, relPath, db.getScriptPath());
            }
            script.setScriptPath(relPath);
            // 源码有更新则写回文件（重命名时同步搬迁）
            if (!relPath.equals(db.getScriptPath())) {
                String oldSource = readSourceSafe(skillCode, db);
                fileService.write(skillCode, relPath, oldSource);
                if (StrUtil.isNotBlank(db.getScriptPath()) && fileService.exists(skillCode)) {
                    try {
                        Path old = fileService.resolve(skillCode, db.getScriptPath());
                        if (Files.isRegularFile(old)) {
                            Files.deleteIfExists(old);
                        }
                    } catch (Exception ignored) {
                        // 旧文件删除失败不影响改名
                    }
                }
            }
        }
        String source = script.getSource();
        if (source != null && StrUtil.isNotBlank(relPath)) {
            fileService.ensureDir(skillCode);
            fileService.write(skillCode, relPath, source);
        }
        script.setContent(null);
        script.setSource(null);
        script.setSkillId(db.getSkillId());
        return scriptMapper.updateById(script);
    }

    @Override
    public int deleteScripts(List<Long> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return 0;
        }
        for (Long id : scriptIds) {
            SkillScript s = scriptMapper.selectById(id);
            if (s == null) {
                continue;
            }
            // 同步删除技能目录下的文件，文件删不掉也不影响记录清理
            if (StrUtil.isNotBlank(s.getScriptPath())) {
                try {
                    String code = resolveSkillCode(s.getSkillId());
                    if (fileService.exists(code)) {
                        fileService.delete(code, s.getScriptPath());
                    }
                } catch (Exception e) {
                    log.warn("删除脚本文件失败 scriptId={}: {}", id, e.getMessage());
                }
            }
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

        ScriptResult result;
        String skillCode = resolveSkillCode(script.getSkillId());
        String relPath = script.getScriptPath();
        if (StrUtil.isNotBlank(relPath) && fileService.exists(skillCode)) {
            Path dir = fileService.dir(skillCode);
            Path file = dir.resolve(relPath.replace('\\', '/')).normalize();
            if (Files.isRegularFile(file)) {
                // 推荐形态：直接执行技能目录里的文件，工作目录为技能目录
                result = executor.runFile(dir, relPath, paramsJson, script.getTimeoutSec());
            } else {
                result = ScriptResult.fail("脚本文件不存在：" + relPath + "，请确认文件已上传或点击「重新扫描」");
            }
        } else if (StrUtil.isNotBlank(script.getContent())) {
            // 兼容：早期把源码存库的脚本，直接执行源码
            result = executor.run(script.getContent(), paramsJson, script.getTimeoutSec());
        } else {
            result = ScriptResult.fail("脚本文件缺失：" + (StrUtil.isBlank(relPath) ? "(未配置文件路径)" : relPath));
        }
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

    // ====================== 目录同步 ======================

    @Override
    public SyncResult syncFromDirectory(Long skillId) {
        SyncResult result = new SyncResult();
        if (skillId == null) {
            return result;
        }
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null || StrUtil.isBlank(skill.getSkillCode())) {
            throw new BusinessException("技能不存在或缺少编码");
        }
        String skillCode = skill.getSkillCode();
        fileService.ensureDir(skillCode);

        List<String> files = fileService.scanPythonScripts(skillCode);
        result.setScanned(files.size());

        List<SkillScript> existing = scriptMapper.selectBySkillId(skillId);
        Set<String> knownPaths = new HashSet<>();
        for (SkillScript s : existing) {
            if (StrUtil.isNotBlank(s.getScriptPath())) {
                knownPaths.add(s.getScriptPath().replace('\\', '/'));
            }
        }

        // 1) 早期内联脚本迁移成文件
        for (SkillScript s : existing) {
            if (StrUtil.isBlank(s.getScriptPath()) && StrUtil.isNotBlank(s.getContent())) {
                String rel = uniqueRelPath(skillCode, defaultScriptPath(s.getScriptCode()), null);
                fileService.write(skillCode, rel, s.getContent());
                SkillScript up = new SkillScript();
                up.setScriptId(s.getScriptId());
                up.setScriptPath(rel);
                up.setContent(null);
                scriptMapper.updateById(up);
                knownPaths.add(rel);
                result.getMigrated().add(s.getScriptCode());
            }
        }

        // 2) 目录里新出现的 py 文件自动注册
        for (String rel : files) {
            if (knownPaths.contains(rel)) {
                continue;
            }
            // 下划线开头的视为供 import 的内部模块，不作为工具暴露给模型
            String fileName = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
            if (fileName.startsWith("_")) {
                continue;
            }
            String code = toScriptCode(rel);
            if (codeExists(skillId, code)) {
                // 编码冲突时加后缀，保证模型调用名唯一
                int i = 2;
                while (codeExists(skillId, code + i)) {
                    i++;
                }
                code = code + i;
            }
            SkillScript s = new SkillScript();
            s.setSkillId(skillId);
            s.setScriptCode(code);
            s.setScriptName(code);
            s.setDescription("来自技能目录文件 " + rel + "，请补充说明用途与参数后启用");
            s.setLanguage("python");
            s.setScriptPath(rel);
            s.setTimeoutSec(30);
            s.setStatus(0);
            scriptMapper.insert(s);
            result.getAdded().add(code);
        }

        // 3) 标记文件已丢失的脚本
        for (SkillScript s : existing) {
            if (StrUtil.isBlank(s.getScriptPath())) {
                continue;
            }
            boolean still = files.contains(s.getScriptPath().replace('\\', '/'));
            if (!still) {
                Path p = fileService.dir(skillCode).resolve(s.getScriptPath().replace('\\', '/')).normalize();
                if (!Files.isRegularFile(p)) {
                    result.getMissing().add(s.getScriptCode());
                }
            }
        }
        return result;
    }

    @Override
    public String readScriptSource(Long scriptId) {
        SkillScript s = scriptMapper.selectById(scriptId);
        if (s == null) {
            throw new BusinessException("脚本不存在");
        }
        String skillCode = resolveSkillCode(s.getSkillId());
        if (StrUtil.isNotBlank(s.getScriptPath()) && fileService.exists(skillCode)) {
            try {
                return fileService.read(skillCode, s.getScriptPath());
            } catch (Exception e) {
                log.warn("读取脚本文件失败 {}：{}", s.getScriptPath(), e.getMessage());
            }
        }
        return StrUtil.isNotBlank(s.getContent()) ? s.getContent() : "";
    }

    @Override
    public void writeScriptSource(Long scriptId, String source) {
        SkillScript s = scriptMapper.selectById(scriptId);
        if (s == null) {
            throw new BusinessException("脚本不存在");
        }
        String skillCode = resolveSkillCode(s.getSkillId());
        String rel = StrUtil.isNotBlank(s.getScriptPath()) ? s.getScriptPath() : defaultScriptPath(s.getScriptCode());
        fileService.ensureDir(skillCode);
        fileService.write(skillCode, rel, source);
        if (!rel.equals(s.getScriptPath())) {
            SkillScript up = new SkillScript();
            up.setScriptId(scriptId);
            up.setScriptPath(rel);
            scriptMapper.updateById(up);
        }
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
        sb.append("下面的工具是技能目录下真实存在的 Python 脚本，执行时以技能目录为工作目录，\n");
        sb.append("脚本可以读取目录内的资源文件并 import 其它模块。当任务确实需要真实计算、查询或处理数据时才调用，\n");
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

    /** 技能ID → 技能编码 */
    private String resolveSkillCode(Long skillId) {
        if (skillId == null) {
            throw new BusinessException("所属技能不能为空");
        }
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null || StrUtil.isBlank(skill.getSkillCode())) {
            throw new BusinessException("所属技能不存在或缺少编码");
        }
        return skill.getSkillCode();
    }

    /** 默认脚本路径 scripts/<code>.py */
    private String defaultScriptPath(String scriptCode) {
        return "scripts/" + StrUtil.trimToEmpty(scriptCode) + ".py";
    }

    private String normalizeRelPath(String path) {
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.toLowerCase(Locale.ROOT).endsWith(".py")) {
            p = p + ".py";
        }
        return p;
    }

    /** 若目标文件已存在且属于别的脚本，加序号避免覆盖 */
    private String uniqueRelPath(String skillCode, String relPath, String ignorePath) {
        if (!fileService.exists(skillCode)) {
            return relPath;
        }
        Path base = fileService.dir(skillCode);
        String candidate = relPath;
        int i = 2;
        while (Files.isRegularFile(base.resolve(candidate).normalize()) && !candidate.equals(ignorePath)) {
            String stem = relPath.substring(0, relPath.length() - 3);
            candidate = stem + "_" + i + ".py";
            i++;
        }
        return candidate;
    }

    /** 由文件路径推导脚本编码：取文件名主干，非法字符替换为下划线 */
    private String toScriptCode(String relPath) {
        String name = relPath.contains("/") ? relPath.substring(relPath.lastIndexOf('/') + 1) : relPath;
        if (name.toLowerCase(Locale.ROOT).endsWith(".py")) {
            name = name.substring(0, name.length() - 3);
        }
        String code = name.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return StrUtil.isBlank(code) ? ("script_" + Math.abs(relPath.hashCode())) : code.toLowerCase(Locale.ROOT);
    }

    private boolean codeExists(Long skillId, String code) {
        if (StrUtil.isBlank(code)) {
            return false;
        }
        return scriptMapper.selectCount(new LambdaQueryWrapper<SkillScript>()
                .eq(SkillScript::getSkillId, skillId)
                .eq(SkillScript::getScriptCode, code)) > 0;
    }

    /** 读取脚本源码，优先文件，回退数据库 */
    private String readSourceSafe(String skillCode, SkillScript script) {
        if (StrUtil.isNotBlank(script.getScriptPath()) && fileService.exists(skillCode)) {
            try {
                return fileService.read(skillCode, script.getScriptPath());
            } catch (Exception ignored) {
                // 落到下面的兜底
            }
        }
        return StrUtil.isNotBlank(script.getContent()) ? script.getContent() : "";
    }

    /** 标记脚本文件是否存在，便于前端提示 */
    private void markFileExists(List<SkillScript> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }
        Set<Long> skillIds = new LinkedHashSet<>();
        for (SkillScript s : scripts) {
            if (s.getSkillId() != null) {
                skillIds.add(s.getSkillId());
            }
        }
        java.util.Map<Long, String> codeMap = new java.util.HashMap<>();
        for (Long id : skillIds) {
            Skill sk = skillMapper.selectById(id);
            if (sk != null) {
                codeMap.put(id, sk.getSkillCode());
            }
        }
        for (SkillScript s : scripts) {
            if (StrUtil.isBlank(s.getScriptPath())) {
                // 内联脚本：content 有值视为可用
                s.setFileExists(StrUtil.isNotBlank(s.getContent()));
                continue;
            }
            String code = codeMap.get(s.getSkillId());
            boolean ok = StrUtil.isNotBlank(code) && fileService.exists(code)
                    && Files.isRegularFile(fileService.dir(code).resolve(s.getScriptPath().replace('\\', '/')).normalize());
            s.setFileExists(ok);
        }
    }

    /** 新建脚本时给一段能跑通的模板，避免用户面对空文件 */
    private String defaultScriptTemplate(String scriptCode, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 技能脚本：").append(StrUtil.trimToEmpty(scriptCode)).append("\n");
        if (StrUtil.isNotBlank(description)) {
            sb.append("# 用途：").append(description.trim()).append("\n");
        }
        sb.append("#\n");
        sb.append("# 入参通过环境变量 SKILL_PARAMS（JSON 字符串）传入，也可以用 SKILL_INPUT_FILE 指向的 input.json。\n");
        sb.append("# 工作目录是技能目录，可以相对路径读取资源文件，也可以 import 本目录下的其它模块。\n");
        sb.append("# 结果直接 print 到标准输出，建议输出 JSON。\n\n");
        sb.append("import json, os\n\n");
        sb.append("def main(args):\n");
        sb.append("    # TODO 在这里实现你的逻辑\n");
        sb.append("    return {\"ok\": True, \"echo\": args}\n\n\n");
        sb.append("if __name__ == \"__main__\":\n");
        sb.append("    raw = os.environ.get(\"SKILL_PARAMS\") or \"{}\"\n");
        sb.append("    try:\n");
        sb.append("        args = json.loads(raw)\n");
        sb.append("    except Exception:\n");
        sb.append("        args = {}\n");
        sb.append("    print(json.dumps(main(args), ensure_ascii=False))\n");
        return sb.toString();
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
