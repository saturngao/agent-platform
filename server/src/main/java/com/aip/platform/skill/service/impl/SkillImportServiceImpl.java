package com.aip.platform.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.file.SkillFileService;
import com.aip.platform.skill.mapper.SkillMapper;
import com.aip.platform.skill.service.ISkillImportService;
import com.aip.platform.skill.service.ISkillScriptService;
import com.aip.platform.skill.service.ISkillService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能导入实现
 *
 * <p>导入流程：先落到一个临时技能目录 → 解析 SKILL.md 拿到元信息 → 确定技能编码 →
 * 目录改名为正式编码 → 落库 → 扫描 scripts/ 下的 .py 自动注册为可调用工具。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillImportServiceImpl implements ISkillImportService {

    private final SkillFileService fileService;
    private final SkillMapper skillMapper;
    private final ISkillService skillService;
    private final ISkillScriptService scriptService;

    /** 单文件上限 5MB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /** 单次导入文件数上限 */
    private static final int MAX_FILE_COUNT = 300;

    /** frontmatter 块 */
    private static final Pattern FM_BLOCK = Pattern.compile("^\\s*---\\s*\\R([\\s\\S]*?)\\R\\s*---\\s*\\R?");

    @Override
    public Skill importFromFiles(List<MultipartFile> files, List<String> relPaths, String skillCode, boolean overwrite) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("没有上传任何文件");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new BusinessException("文件数量超过上限 " + MAX_FILE_COUNT);
        }
        String tmp = tmpCode();
        String stripPrefix = computeStripPrefix(files, relPaths);
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new BusinessException("文件 " + file.getOriginalFilename() + " 超过 5MB");
                }
                String raw = relPaths != null && i < relPaths.size() ? relPaths.get(i) : null;
                if (StrUtil.isBlank(raw)) {
                    raw = file.getOriginalFilename();
                }
                String rel = stripPrefix(raw == null ? "" : raw, stripPrefix);
                if (StrUtil.isBlank(rel)) {
                    throw new BusinessException("文件缺少有效路径：" + file.getOriginalFilename());
                }
                if (!fileService.isAllowed(rel)) {
                    // 白名单外的文件直接跳过，避免把可执行二进制带进技能目录
                    log.warn("导入技能跳过不允许的文件类型：{}", rel);
                    continue;
                }
                fileService.writeBytes(tmp, rel, file.getBytes());
            }
            return finishImport(tmp, firstDirName(relPaths), skillCode, overwrite);
        } catch (BusinessException e) {
            fileService.deleteSkillDir(tmp);
            throw e;
        } catch (Exception e) {
            fileService.deleteSkillDir(tmp);
            throw new BusinessException("导入失败：" + e.getMessage());
        }
    }

    @Override
    public Skill importFromZip(InputStream in, String originName, String skillCode, boolean overwrite) {
        String tmp = tmpCode();
        try {
            fileService.extractZip(tmp, in);
            if (!fileService.exists(tmp)) {
                throw new BusinessException("压缩包为空或没有可导入的文件");
            }
            // 压缩包通常外层还有一个目录，先剥掉；剥掉的目录名正好作为技能编码的候选
            String topDir = fileService.stripCommonTopDir(tmp);
            String fallback = originName == null ? null : originName.replaceAll("\\.zip$", "");
            return finishImport(tmp, StrUtil.isNotBlank(topDir) ? topDir : fallback, skillCode, overwrite);
        } catch (BusinessException e) {
            fileService.deleteSkillDir(tmp);
            throw e;
        } catch (Exception e) {
            fileService.deleteSkillDir(tmp);
            throw new BusinessException("导入失败：" + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> preview(InputStream in) {
        String tmp = tmpCode();
        try {
            fileService.extractZip(tmp, in);
            Path dir = fileService.dir(tmp);
            String skillMd = findSkillMarkdown(tmp);
            Map<String, Object> meta = new LinkedHashMap<>();
            if (skillMd != null) {
                String text = fileService.read(tmp, skillMd);
                meta = parseMeta(text);
            }
            List<String> scripts = fileService.scanPythonScripts(tmp);
            List<String> allFiles = flatten(fileService.tree(tmp));
            Map<String, Object> result = new LinkedHashMap<>(meta);
            result.put("skillMd", skillMd);
            result.put("scripts", scripts);
            result.put("files", allFiles);
            result.put("fileCount", allFiles.size());
            return result;
        } finally {
            fileService.deleteSkillDir(tmp);
        }
    }

    // ====================== 私有方法 ======================

    /** 把临时目录转正为正式技能目录并落库 */
    private Skill finishImport(String tmpCode, String fallbackName, String wantCode, boolean overwrite) {
        Path dir = fileService.dir(tmpCode);
        if (!Files.isDirectory(dir)) {
            throw new BusinessException("没有解析到任何可导入的文件");
        }

        String skillMd = findSkillMarkdown(tmpCode);
        Map<String, Object> meta = new LinkedHashMap<>();
        if (skillMd != null) {
            meta = parseMeta(fileService.read(tmpCode, skillMd));
        }

        String name = str(meta.get("name"));
        if (StrUtil.isBlank(name)) {
            name = StrUtil.isBlank(fallbackName) ? "导入技能" : fallbackName;
        }
        // 编码优先级：用户指定 > frontmatter 里的 skill_code/code > 目录名 > 技能名
        String code = StrUtil.isNotBlank(wantCode) ? toCode(wantCode) : null;
        if (StrUtil.isBlank(code)) {
            code = toCode(str(meta.get("skill_code")));
        }
        if (StrUtil.isBlank(code)) {
            code = toCode(str(meta.get("code")));
        }
        if (StrUtil.isBlank(code)) {
            code = toCode(fallbackName);
        }
        if (StrUtil.isBlank(code)) {
            code = toCode(name);
        }
        if (StrUtil.isBlank(code)) {
            code = "skill-" + UUID.randomUUID().toString().substring(0, 8);
        }
        code = resolveConflict(code, overwrite);

        // 目录改名到正式编码
        try {
            Path target = fileService.dir(code);
            if (Files.exists(target)) {
                fileService.deleteSkillDir(code);
            }
            Files.createDirectories(target.getParent());
            Files.move(dir, target);
        } catch (Exception e) {
            throw new BusinessException("写入技能目录失败：" + e.getMessage());
        }

        String body = str(meta.get("body"));
        String summary = str(meta.get("description"));
        if (StrUtil.isBlank(body) && skillMd == null) {
            // 没有 SKILL.md 时生成一份占位说明，保证技能内容不为空
            body = "# " + name + "\n\n> 由目录导入的技能，尚未编写 SKILL.md 说明。\n\n"
                    + "## 适用场景\n\n（待补充）\n\n## 使用方式\n\n（待补充）\n";
            fileService.write(code, "SKILL.md", body);
        }
        if (StrUtil.isBlank(body)) {
            body = summary;
        }
        if (StrUtil.isBlank(body)) {
            body = "# " + name + "\n";
        }
        if (StrUtil.isBlank(summary)) {
            summary = body.trim().length() > 80 ? body.trim().substring(0, 80) : body.trim();
        }

        Skill skill = new Skill();
        skill.setSkillCode(code);
        skill.setSkillName(name);
        skill.setSummary(summary);
        skill.setContent(body);
        skill.setSource("IMPORT");
        skill.setCategory(str(meta.get("category")));
        skill.setIcon(StrUtil.isBlank(str(meta.get("icon"))) ? "📦" : str(meta.get("icon")));
        skill.setAuthor(StrUtil.isBlank(str(meta.get("author")))
                ? defaultUser() : str(meta.get("author")));
        skill.setVersion(StrUtil.isBlank(str(meta.get("version"))) ? "1.0.0" : str(meta.get("version")));
        skill.setTags(str(meta.get("tags")));
        skill.setStatus(0);
        skill.setDirFlag(1);
        skillService.insertSkill(skill);

        // 扫描目录自动注册脚本工具
        try {
            scriptService.syncFromDirectory(skill.getSkillId());
        } catch (Exception e) {
            log.warn("导入后同步脚本失败 {}: {}", code, e.getMessage());
        }
        return skill;
    }

    /** 编码冲突处理：覆盖则清掉旧的，否则追加序号 */
    private String resolveConflict(String code, boolean overwrite) {
        LambdaQueryWrapper<Skill> q = new LambdaQueryWrapper<Skill>().eq(Skill::getSkillCode, code);
        Skill old = skillMapper.selectOne(q, false);
        if (old == null) {
            return code;
        }
        if (overwrite) {
            // 清掉旧的脚本记录与目录，再用同一编码重建
            try {
                scriptService.deleteScripts(scriptIdsOf(old.getSkillId()));
            } catch (Exception e) {
                log.warn("清理旧脚本记录失败 {}: {}", code, e.getMessage());
            }
            skillMapper.deleteById(old.getSkillId());
            fileService.deleteSkillDir(code);
            return code;
        }
        int i = 2;
        while (skillMapper.selectOne(new LambdaQueryWrapper<Skill>().eq(Skill::getSkillCode, code + "-" + i), false) != null) {
            i++;
        }
        return code + "-" + i;
    }

    private List<Long> scriptIdsOf(Long skillId) {
        List<Long> ids = new ArrayList<>();
        List<com.aip.platform.skill.entity.SkillScript> list =
                scriptService.selectBySkillId(skillId);
        if (list != null) {
            for (com.aip.platform.skill.entity.SkillScript s : list) {
                ids.add(s.getScriptId());
            }
        }
        return ids;
    }

    /** 查找技能说明文件：优先 SKILL.md，其次任意 .md */
    private String findSkillMarkdown(String code) {
        List<String> mds = new ArrayList<>();
        collectMarkdown(fileService.tree(code), mds);
        if (mds.isEmpty()) {
            return null;
        }
        for (String m : mds) {
            String lower = m.toLowerCase(Locale.ROOT);
            if (lower.equals("skill.md") || lower.endsWith("/skill.md")) {
                return m;
            }
        }
        // 退而求其次：根目录下第一个 md
        for (String m : mds) {
            if (!m.contains("/")) {
                return m;
            }
        }
        return mds.get(0);
    }

    private void collectMarkdown(List<com.aip.platform.skill.file.SkillFileNode> nodes, List<String> out) {
        if (nodes == null) {
            return;
        }
        for (com.aip.platform.skill.file.SkillFileNode n : nodes) {
            if (Boolean.TRUE.equals(n.getDir())) {
                collectMarkdown(n.getChildren(), out);
            } else if (n.getPath() != null && n.getPath().toLowerCase(Locale.ROOT).endsWith(".md")) {
                out.add(n.getPath());
            }
        }
    }

    /** 解析 SKILL.md 的 YAML frontmatter（只解析简单 key: value） */
    private Map<String, Object> parseMeta(String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (StrUtil.isBlank(text)) {
            return meta;
        }
        String body = text;
        Matcher m = FM_BLOCK.matcher(text);
        if (m.find()) {
            String fm = m.group(1);
            body = text.substring(m.end());
            String key = null;
            StringBuilder value = new StringBuilder();
            for (String line : fm.split("\\R")) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }
                boolean indented = line.startsWith(" ") || line.startsWith("\t");
                int idx = indented ? -1 : line.indexOf(':');
                if (!indented && idx > 0) {
                    flush(meta, key, value);
                    key = line.substring(0, idx).trim();
                    value = new StringBuilder(line.substring(idx + 1).trim());
                } else if (key != null) {
                    if (value.length() > 0) {
                        value.append(' ');
                    }
                    value.append(line.trim());
                }
            }
            flush(meta, key, value);
        }
        meta.put("body", body == null ? "" : body);
        return meta;
    }

    private void flush(Map<String, Object> meta, String key, StringBuilder value) {
        if (key == null) {
            return;
        }
        String v = value.toString().trim();
        // 去掉引号
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1);
        }
        meta.put(key.toLowerCase(Locale.ROOT), v);
    }

    /** 扁平化文件树为相对路径列表 */
    private List<String> flatten(List<com.aip.platform.skill.file.SkillFileNode> nodes) {
        List<String> out = new ArrayList<>();
        if (nodes == null) {
            return out;
        }
        for (com.aip.platform.skill.file.SkillFileNode n : nodes) {
            if (Boolean.TRUE.equals(n.getDir())) {
                out.addAll(flatten(n.getChildren()));
            } else {
                out.add(n.getPath());
            }
        }
        return out;
    }

    /**
     * 计算需要剥离的公共顶层目录
     *
     * <p>文件夹上传时浏览器给出的路径是「选中的目录名/子路径」，
     * 如果所有文件都在同一个顶层目录下，导入时应把这一层去掉，
     * 否则技能目录会多套一层。</p>
     */
    private String computeStripPrefix(List<MultipartFile> files, List<String> relPaths) {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f == null || f.isEmpty()) {
                continue;
            }
            String raw = relPaths != null && i < relPaths.size() ? relPaths.get(i) : f.getOriginalFilename();
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String p = raw.replace('\\', '/');
            while (p.startsWith("/")) {
                p = p.substring(1);
            }
            if (p.contains("/")) {
                all.add(p);
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        String first = all.get(0).substring(0, all.get(0).indexOf('/'));
        for (String p : all) {
            if (!p.startsWith(first + "/")) {
                return null;
            }
        }
        return first;
    }

    /** 按前缀剥离路径并做基础清洗 */
    private String stripPrefix(String rel, String prefix) {
        String p = rel == null ? "" : rel.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (prefix != null && p.startsWith(prefix + "/")) {
            p = p.substring(prefix.length() + 1);
        }
        return p;
    }

    /** 从相对路径里取第一段作为兜底名称 */
    private String firstDirName(List<String> relPaths) {
        if (relPaths == null || relPaths.isEmpty()) {
            return null;
        }
        String first = relPaths.get(0);
        if (StrUtil.isBlank(first)) {
            return null;
        }
        String p = first.replace('\\', '/');
        String[] parts = p.split("/");
        if (parts.length > 1) {
            return parts[0];
        }
        return null;
    }

    /**
     * 转成合法技能编码：保留大小写字母、数字、中文与 - _，其余字符转连字符
     *
     * <p>中文技能名会保留下来（如「SQL 成本顾问」→ sql-成本顾问），
     * 只有目录名这种更规范的来源优先时才会用到英文编码。</p>
     */
    private String toCode(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s]+", "-")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5_\\-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        return c.isEmpty() ? null : c;
    }

    private String defaultUser() {
        try {
            String u = SecurityUtils.getUsername();
            return StrUtil.isBlank(u) ? "import" : u;
        } catch (Exception e) {
            return "import";
        }
    }

    private String tmpCode() {
        return "__import_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 读取文本文件兜底（保留给后续扩展） */
    private String readQuietly(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
