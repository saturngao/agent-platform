package com.aip.platform.skill.controller;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.R;
import com.aip.platform.skill.entity.SkillScript;
import com.aip.platform.skill.entity.SkillScriptLog;
import com.aip.platform.skill.script.PythonScriptExecutor;
import com.aip.platform.skill.script.ScriptResult;
import com.aip.platform.skill.service.ISkillScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能脚本接口
 *
 * <p>独立挂在 /api/skill-script 下，避免与 /api/skill/{skillId} 的路径参数产生歧义。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/skill-script")
@RequiredArgsConstructor
public class SkillScriptController {

    private final ISkillScriptService scriptService;
    private final PythonScriptExecutor executor;
    private final ObjectMapper objectMapper;

    /** 某技能下的脚本列表 */
    @GetMapping({"", "/list"})
    public R<List<SkillScript>> list(@RequestParam(value = "skillId", required = false) Long skillId) {
        return R.ok(scriptService.selectBySkillId(skillId));
    }

    @GetMapping("/{scriptId}")
    public R<SkillScript> detail(@PathVariable("scriptId") Long scriptId) {
        return R.ok(scriptService.selectDetail(scriptId));
    }

    @PreAuthorize("@permissionChecker.has('skill:script:edit')")
    @PostMapping({"", "/add"})
    public R<SkillScript> add(@RequestBody SkillScript script) {
        scriptService.insertScript(script);
        return R.ok(script);
    }

    @PreAuthorize("@permissionChecker.has('skill:script:edit')")
    @PutMapping({"", "/edit"})
    public R<SkillScript> edit(@RequestBody SkillScript script) {
        scriptService.updateScript(script);
        return R.ok(script);
    }

    @PreAuthorize("@permissionChecker.has('skill:script:edit')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        scriptService.deleteScripts(ids);
        return R.ok();
    }

    /**
     * 运行已保存的脚本
     * body: { scriptId, params } —— params 可以是 JSON 对象，也可以是 JSON 字符串
     */
    @PreAuthorize("@permissionChecker.has('skill:script:run')")
    @PostMapping("/run")
    public R<ScriptResult> run(@RequestBody Map<String, Object> body) {
        Long scriptId = toLong(body == null ? null : body.get("scriptId"));
        if (scriptId == null) {
            return R.fail("脚本ID不能为空");
        }
        String params = toJson(body == null ? null : body.get("params"));
        return R.ok(scriptService.run(scriptId, params, ISkillScriptService.BIZ_MANUAL, null));
    }

    /**
     * 在线调试：直接执行一段源码，不落库为脚本
     * body: { code, params, timeoutSec, skillId }
     */
    @PreAuthorize("@permissionChecker.has('skill:script:run')")
    @PostMapping("/run-inline")
    public R<ScriptResult> runInline(@RequestBody Map<String, Object> body) {
        if (body == null || StrUtil.isBlank((String) body.get("code"))) {
            return R.fail("脚本内容不能为空");
        }
        String code = (String) body.get("code");
        String params = toJson(body.get("params"));
        Integer timeout = body.get("timeoutSec") == null ? null : ((Number) body.get("timeoutSec")).intValue();
        Long skillId = toLong(body.get("skillId"));
        return R.ok(scriptService.runInline(code, params, timeout, skillId));
    }

    /**
     * 读取脚本源码
     *
     * <p>优先返回技能目录里的文件内容；文件缺失时回退数据库里的历史源码。</p>
     */
    @GetMapping("/{scriptId}/content")
    public R<Map<String, Object>> content(@PathVariable("scriptId") Long scriptId) {
        SkillScript script = scriptService.selectDetail(scriptId);
        if (script == null) {
            return R.fail("脚本不存在");
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("scriptId", script.getScriptId());
        data.put("scriptCode", script.getScriptCode());
        data.put("scriptPath", script.getScriptPath());
        data.put("skillId", script.getSkillId());
        data.put("fileExists", script.getFileExists());
        data.put("content", scriptService.readScriptSource(scriptId));
        return R.ok(data);
    }

    /** 保存脚本源码到技能目录文件 */
    @PreAuthorize("@permissionChecker.has('skill:script:edit')")
    @PutMapping("/{scriptId}/content")
    public R<Void> saveContent(@PathVariable("scriptId") Long scriptId, @RequestBody Map<String, Object> body) {
        String content = body == null ? null : (String) body.get("content");
        if (StrUtil.isBlank(content)) {
            return R.fail("脚本内容不能为空");
        }
        scriptService.writeScriptSource(scriptId, content);
        return R.ok();
    }

    /**
     * 重新扫描技能目录，把新增的 .py 自动注册成工具
     * body: { skillId }
     */
    @PreAuthorize("@permissionChecker.has('skill:script:edit')")
    @PostMapping("/sync")
    public R<ISkillScriptService.SyncResult> sync(@RequestBody Map<String, Object> body) {
        Long skillId = toLong(body == null ? null : body.get("skillId"));
        if (skillId == null) {
            return R.fail("技能ID不能为空");
        }
        return R.ok(scriptService.syncFromDirectory(skillId));
    }

    /** 执行记录 */
    @GetMapping("/log")
    public R<List<SkillScriptLog>> logs(@RequestParam("scriptId") Long scriptId,
                                        @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(scriptService.selectLogs(scriptId, limit));
    }

    /** 查看当前生效的执行器配置，便于排查环境问题 */
    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("enabled", executor.isEnabled());
        data.put("details", executor.describeConfig());
        return R.ok(data);
    }

    // ====================== 工具方法 ======================

    /** 把入参（对象或字符串）统一序列化成 JSON 字符串 */
    private String toJson(Object params) {
        if (params == null) {
            return "{}";
        }
        if (params instanceof String) {
            String s = ((String) params).trim();
            return s.isEmpty() ? "{}" : s;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 预留：批量查询多个技能的脚本 */
    @PostMapping("/batch")
    public R<List<SkillScript>> batch(@RequestBody Map<String, Object> body) {
        Object ids = body == null ? null : body.get("skillIds");
        List<Long> skillIds = new ArrayList<>();
        if (ids instanceof List) {
            for (Object item : (List<?>) ids) {
                Long v = toLong(item);
                if (v != null) {
                    skillIds.add(v);
                }
            }
        }
        return R.ok(scriptService.selectEnabledBySkillIds(skillIds));
    }
}
