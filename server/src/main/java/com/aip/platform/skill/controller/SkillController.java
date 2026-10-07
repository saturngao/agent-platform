package com.aip.platform.skill.controller;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.file.SkillFileNode;
import com.aip.platform.skill.file.SkillFileService;
import com.aip.platform.skill.service.ISkillImportService;
import com.aip.platform.skill.service.ISkillScriptService;
import com.aip.platform.skill.service.ISkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能模块接口
 *
 * <p>REST 双映射，兼容 /skill 与 /skill/add 两种写法，与系统其它模块保持一致。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;
    private final ISkillScriptService scriptService;
    private final ISkillImportService importService;
    private final SkillFileService fileService;

    /** frontmatter 中的 name 字段 */
    private static final Pattern FM_NAME = Pattern.compile("(?m)^\\s*name\\s*[:：]\\s*(.+)$");
    /** frontmatter 中的 description 字段 */
    private static final Pattern FM_DESC = Pattern.compile("(?m)^\\s*description\\s*[:：]\\s*(.+)$");
    /** frontmatter 块：--- 开头与结尾 */
    private static final Pattern FM_BLOCK = Pattern.compile("^\\s*---\\s*\\n(.*?)\\n\\s*---\\s*\\n", Pattern.DOTALL);

    @GetMapping({"", "/list"})
    public R<PageResult<Skill>> list(Skill q, PageQuery query) {
        return R.ok(skillService.selectPage(query, q));
    }

    @GetMapping("/{skillId}")
    public R<Skill> detail(@PathVariable("skillId") Long skillId) {
        return R.ok(skillService.selectDetail(skillId));
    }

    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody Skill skill) {
        skillService.insertSkill(skill);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody Skill skill) {
        skillService.updateSkill(skill);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        skillService.deleteSkills(ids);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PutMapping("/status")
    public R<Void> status(@RequestBody Skill skill) {
        skillService.changeStatus(skill.getSkillId(), skill.getStatus());
        return R.ok();
    }

    /** 下拉选项：只返回正常状态的技能 */
    @GetMapping("/optionselect")
    public R<List<Map<String, Object>>> optionselect() {
        return R.ok(skillService.selectOptions());
    }

    /** 查询某业务对象已挂载的技能ID */
    @GetMapping("/binding")
    public R<List<Long>> binding(@RequestParam("bizType") String bizType,
                                 @RequestParam("bizId") Long bizId) {
        return R.ok(skillService.selectSkillIdsByBiz(bizType, bizId));
    }

    /** 重建某业务对象的技能绑定 */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PostMapping("/binding")
    public R<Void> bind(@RequestBody Map<String, Object> body) {
        String bizType = body == null ? null : (String) body.get("bizType");
        Long bizId = body == null ? null : toLong(body.get("bizId"));
        List<Long> skillIds = body == null ? null : toLongList(body.get("skillIds"));
        skillService.bindSkills(bizType, bizId, skillIds);
        return R.ok();
    }

    /**
     * 导入技能：粘贴 SKILL.md 全文，自动解析 frontmatter 里的 name / description，
     * 解析不到时用默认值兜底。
     */
    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping("/import")
    public R<Skill> importSkill(@RequestBody Map<String, Object> body) {
        if (body == null || StrUtil.isBlank((String) body.get("content"))) {
            return R.fail("技能内容不能为空");
        }
        String content = (String) body.get("content");
        String name = str(body.get("skillName"));
        String summary = str(body.get("summary"));

        // 解析 YAML frontmatter
        Matcher blockMatcher = FM_BLOCK.matcher(content);
        if (blockMatcher.find()) {
            String fm = blockMatcher.group(1);
            Matcher nm = FM_NAME.matcher(fm);
            if (nm.find() && StrUtil.isBlank(name)) {
                name = nm.group(1).trim();
            }
            Matcher dm = FM_DESC.matcher(fm);
            if (dm.find() && StrUtil.isBlank(summary)) {
                summary = dm.group(1).trim();
            }
        }
        if (StrUtil.isBlank(name)) {
            name = "导入技能-" + System.currentTimeMillis();
        }
        if (StrUtil.isBlank(summary)) {
            summary = StrUtil.isBlank(content) ? "" : content.trim().substring(0, Math.min(80, content.trim().length()));
        }

        // 编码：优先用传入值，否则按名称生成并保证唯一
        String code = str(body.get("skillCode"));
        if (StrUtil.isBlank(code)) {
            code = "import-" + UUID.randomUUID().toString().substring(0, 8);
        }

        Skill skill = new Skill();
        skill.setSkillCode(code);
        skill.setSkillName(name);
        skill.setSummary(summary);
        skill.setContent(content);
        skill.setSource(StrUtil.isBlank(str(body.get("source"))) ? "IMPORT" : str(body.get("source")));
        skill.setCategory(str(body.get("category")));
        skill.setIcon(StrUtil.isBlank(str(body.get("icon"))) ? "📦" : str(body.get("icon")));
        skill.setAuthor(StrUtil.isBlank(str(body.get("author"))) ? SecurityUtils.getUsername() : str(body.get("author")));
        skill.setVersion(StrUtil.isBlank(str(body.get("version"))) ? "1.0.0" : str(body.get("version")));
        skill.setStatus(0);

        skillService.insertSkill(skill);
        return R.ok(skill);
    }

    // ====================== 技能目录与文件 ======================

    /** 技能文件根目录，便于排查与提示 */
    @GetMapping("/root")
    public R<Map<String, Object>> skillRoot() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("path", fileService.rootPath());
        return R.ok(data);
    }

    /** 技能目录下的文件树 */
    @GetMapping("/{skillId}/files")
    public R<List<SkillFileNode>> files(@PathVariable("skillId") Long skillId) {
        return R.ok(fileService.tree(codeOf(skillId)));
    }

    /** 读取技能目录下某个文件 */
    @GetMapping("/{skillId}/file")
    public R<Map<String, Object>> readFile(@PathVariable("skillId") Long skillId,
                                           @RequestParam("path") String path) {
        String code = codeOf(skillId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("path", path);
        data.put("content", fileService.read(code, path));
        return R.ok(data);
    }

    /** 保存（新增或覆盖）技能目录下的文件 */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PutMapping("/{skillId}/file")
    public R<Void> writeFile(@PathVariable("skillId") Long skillId, @RequestBody Map<String, Object> body) {
        String path = body == null ? null : (String) body.get("path");
        String content = body == null ? null : (String) body.get("content");
        if (StrUtil.isBlank(path)) {
            return R.fail("文件路径不能为空");
        }
        fileService.write(codeOf(skillId), path, content == null ? "" : content);
        return R.ok();
    }

    /** 删除技能目录下的文件或目录 */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @DeleteMapping("/{skillId}/file")
    public R<Void> deleteFile(@PathVariable("skillId") Long skillId, @RequestParam("path") String path) {
        fileService.delete(codeOf(skillId), path);
        return R.ok();
    }

    /** 往已有技能目录追加文件（支持文件夹上传） */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PostMapping("/{skillId}/upload")
    public R<Map<String, Object>> uploadFiles(@PathVariable("skillId") Long skillId,
                                              @RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(value = "paths", required = false) String pathsJson) {
        String code = codeOf(skillId);
        List<String> relPaths = parsePaths(pathsJson);
        int count = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }
            String raw = i < relPaths.size() ? relPaths.get(i) : file.getOriginalFilename();
            String rel = stripPrefix(raw, null);
            if (StrUtil.isBlank(rel) || !fileService.isAllowed(rel)) {
                continue;
            }
            try {
                fileService.writeBytes(code, rel, file.getBytes());
                count++;
            } catch (Exception e) {
                log.warn("写入技能文件失败 {}: {}", rel, e.getMessage());
            }
        }
        // 追加后顺带同步脚本，新增的 .py 立即成为可用工具
        try {
            scriptService.syncFromDirectory(skillId);
        } catch (Exception e) {
            log.warn("上传后同步脚本失败: {}", e.getMessage());
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("count", count);
        return R.ok(data);
    }

    /** 重新扫描技能目录：刷新 SKILL.md 正文并同步脚本 */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PostMapping("/{skillId}/reload")
    public R<Map<String, Object>> reload(@PathVariable("skillId") Long skillId) {
        Skill skill = skillService.selectDetail(skillId);
        String code = skill == null ? null : skill.getSkillCode();
        if (StrUtil.isBlank(code)) {
            return R.fail("技能不存在");
        }
        Map<String, Object> data = new HashMap<>(4);
        // 目录里的 SKILL.md 有更新则同步回数据库
        List<SkillFileNode> nodes = fileService.tree(code);
        String md = findMarkdown(nodes);
        if (md != null) {
            String text = fileService.read(code, md);
            Skill up = new Skill();
            up.setSkillId(skillId);
            up.setContent(text);
            skillService.updateSkill(up);
        }
        data.put("skillMd", md);
        data.put("scripts", fileService.scanPythonScripts(code));
        try {
            data.put("sync", scriptService.syncFromDirectory(skillId));
        } catch (Exception e) {
            data.put("syncError", e.getMessage());
        }
        return R.ok(data);
    }

    /** 导出技能目录为 ZIP */
    @GetMapping("/{skillId}/export")
    public ResponseEntity<byte[]> export(@PathVariable("skillId") Long skillId) {
        Skill skill = skillService.selectDetail(skillId);
        String code = skill == null ? null : skill.getSkillCode();
        byte[] data = fileService.zipSkill(code);
        String filename = (code == null ? "skill" : code) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + urlEncode(filename))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    // ====================== 技能包导入 ======================

    /** 上传整个技能文件夹 */
    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping("/import/folder")
    public R<Skill> importFolder(@RequestParam("files") List<MultipartFile> files,
                                 @RequestParam(value = "paths", required = false) String pathsJson,
                                 @RequestParam(value = "skillCode", required = false) String skillCode,
                                 @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        Skill skill = importService.importFromFiles(files, parsePaths(pathsJson), skillCode, overwrite);
        return R.ok(skill);
    }

    /** 上传 ZIP 技能包 */
    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping("/import/zip")
    public R<Skill> importZip(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "skillCode", required = false) String skillCode,
                              @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要上传的 ZIP 文件");
        }
        try (InputStream in = file.getInputStream()) {
            Skill skill = importService.importFromZip(in, file.getOriginalFilename(), skillCode, overwrite);
            return R.ok(skill);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return R.fail("导入失败：" + e.getMessage());
        }
    }

    /** ZIP 导入预览：先看解析出的名称与脚本列表，再决定是否导入 */
    @PostMapping("/import/preview")
    public R<Map<String, Object>> importPreview(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要上传的 ZIP 文件");
        }
        try (InputStream in = file.getInputStream()) {
            return R.ok(importService.preview(in));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return R.fail("解析失败：" + e.getMessage());
        }
    }

    // ====================== 工具方法 ======================

    /** 技能ID → 技能编码（文件目录以编码命名） */
    private String codeOf(Long skillId) {
        Skill skill = skillService.selectDetail(skillId);
        if (skill == null || StrUtil.isBlank(skill.getSkillCode())) {
            throw new BusinessException("技能不存在或缺少编码");
        }
        return skill.getSkillCode();
    }

    /** 解析前端传来的相对路径数组 JSON */
    private List<String> parsePaths(String json) {
        List<String> list = new ArrayList<>();
        if (StrUtil.isBlank(json)) {
            return list;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : node) {
                    list.add(item.asText());
                }
            }
        } catch (Exception e) {
            log.warn("解析上传路径失败: {}", e.getMessage());
        }
        return list;
    }

    /** 去掉路径前缀中的选中目录名与开头的斜杠 */
    private String stripPrefix(String raw, String prefix) {
        String p = raw == null ? "" : raw.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (prefix != null && p.startsWith(prefix + "/")) {
            p = p.substring(prefix.length() + 1);
        }
        return p;
    }

    /** 在文件树里找 SKILL.md，找不到就用第一个 md */
    private String findMarkdown(List<SkillFileNode> nodes) {
        List<String> mds = new ArrayList<>();
        collectMd(nodes, mds);
        if (mds.isEmpty()) {
            return null;
        }
        for (String m : mds) {
            String lower = m.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals("skill.md") || lower.endsWith("/skill.md")) {
                return m;
            }
        }
        return mds.get(0);
    }

    private void collectMd(List<SkillFileNode> nodes, List<String> out) {
        if (nodes == null) {
            return;
        }
        for (SkillFileNode n : nodes) {
            if (Boolean.TRUE.equals(n.getDir())) {
                collectMd(n.getChildren(), out);
            } else if (n.getPath() != null && n.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
                out.add(n.getPath());
            }
        }
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> toLongList(Object o) {
        List<Long> result = new ArrayList<>();
        if (o == null) {
            return result;
        }
        if (o instanceof List) {
            for (Object item : (List<Object>) o) {
                Long v = toLong(item);
                if (v != null) {
                    result.add(v);
                }
            }
        } else if (o instanceof String) {
            for (String part : ((String) o).split(",")) {
                if (StrUtil.isNotBlank(part)) {
                    result.add(Long.parseLong(part.trim()));
                }
            }
        }
        return result;
    }

    /** 预留：技能分类统计，供前端筛选展示 */
    @GetMapping("/categories")
    public R<List<Map<String, Object>>> categories() {
        List<String> cats = new ArrayList<>();
        cats.add("办公");
        cats.add("研发");
        cats.add("运维");
        cats.add("数据");
        cats.add("写作");
        cats.add("其他");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String c : cats) {
            Map<String, Object> m = new HashMap<>(2);
            m.put("label", c);
            m.put("value", c);
            list.add(m);
        }
        return R.ok(list);
    }
}
