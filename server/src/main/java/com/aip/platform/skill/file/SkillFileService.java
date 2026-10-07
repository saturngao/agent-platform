package com.aip.platform.skill.file;

import com.aip.platform.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 技能目录文件服务
 *
 * <p>一个技能对磁盘上的一个目录：{@code <skill-dir>/<skillCode>/}，
 * 目录内通常包含 SKILL.md（技能说明书）与 scripts/（可执行 Python 脚本），
 * 也可以放 references/、assets/ 等任意资源文件，脚本执行时以该目录为工作目录，
 * 因此脚本可以直接相对路径读取资源、import 同目录的其它模块。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>所有相对路径归一化后必须仍在技能目录内，拒绝 .. 与绝对路径（防止目录穿越）</li>
 *   <li>扩展名白名单，拒绝可执行二进制等危险文件</li>
 *   <li>单文件大小、文件总数、目录总大小均有限额</li>
 * </ul>
 */
@Slf4j
@Service
public class SkillFileService {

    /** 技能文件根目录，默认 ./data/skills（相对服务启动目录） */
    @Value("${platform.skill.dir:./data/skills}")
    private String skillDir;

    // ====================== 限额与白名单 ======================

    /** 单个文件上限：5MB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /** 单个技能文件数上限 */
    private static final int MAX_FILE_COUNT = 500;
    /** 单个技能目录总大小上限：50MB */
    private static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024;

    /** 允许写入的扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "py", "md", "txt", "json", "yaml", "yml", "csv", "tsv", "sql",
            "ini", "cfg", "toml", "xml", "html", "log", "properties",
            "png", "jpg", "jpeg", "gif", "svg", "webp"
    );

    /** 允许写入的特殊文件名（无扩展名或约定名） */
    private static final Set<String> ALLOWED_NAMES = Set.of(
            "skill.md", "readme.md", "requirements.txt", "manifest.json", "dockerfile", "makefile"
    );

    /** 扫描脚本时忽略的目录 */
    private static final Set<String> IGNORE_DIRS = Set.of(
            "__pycache__", ".git", ".idea", ".venv", "venv", ".skill-run", "node_modules"
    );

    // ====================== 目录定位 ======================

    /** 技能文件根目录（绝对路径，不存在则创建） */
    public Path root() {
        String raw = skillDir == null || skillDir.trim().isEmpty() ? "./data/skills" : skillDir.trim();
        if (raw.startsWith("~")) {
            raw = System.getProperty("user.home") + raw.substring(1);
        }
        Path base = Paths.get(raw);
        if (!base.isAbsolute()) {
            base = Paths.get(System.getProperty("user.dir")).resolve(base);
        }
        base = base.normalize().toAbsolutePath();
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new BusinessException("技能目录不可用：" + base + "（" + e.getMessage() + "）");
        }
        return base;
    }

    /** 配置的根目录描述，前端展示用 */
    public String rootPath() {
        return root().toString();
    }

    /** 某技能的目录（绝对路径，不自动创建） */
    public Path dir(String skillCode) {
        requireCode(skillCode);
        return root().resolve(skillCode).normalize();
    }

    /** 技能目录是否存在 */
    public boolean exists(String skillCode) {
        return Files.isDirectory(dir(skillCode));
    }

    /** 确保技能目录存在 */
    public Path ensureDir(String skillCode) {
        Path p = dir(skillCode);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new BusinessException("创建技能目录失败：" + e.getMessage());
        }
        return p;
    }

    /**
     * 安全解析：把相对路径解析到技能目录内的真实路径
     *
     * @throws BusinessException 路径非法或越出技能目录
     */
    public Path resolve(String skillCode, String relPath) {
        requireCode(skillCode);
        if (relPath == null || relPath.trim().isEmpty()) {
            throw new BusinessException("文件路径不能为空");
        }
        String cleaned = relPath.replace('\\', '/').trim();
        if (cleaned.startsWith("/") || cleaned.contains("..") || cleaned.matches("^[A-Za-z]:.*")) {
            throw new BusinessException("非法的文件路径：" + relPath);
        }
        Path base = dir(skillCode).normalize().toAbsolutePath();
        Path target = base.resolve(cleaned).normalize().toAbsolutePath();
        if (!target.startsWith(base)) {
            throw new BusinessException("文件路径越出技能目录：" + relPath);
        }
        return target;
    }

    // ====================== 文件树 ======================

    /** 列出技能目录下的文件树（目录排前，其余按名称排序） */
    public List<SkillFileNode> tree(String skillCode) {
        Path base = dir(skillCode);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<SkillFileNode> roots = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = rel(base, file);
                    if (!isHiddenPath(rel)) {
                        addToTree(roots, rel, false, attrs.size(), attrs.lastModifiedTime().toMillis());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String rel = rel(base, d);
                    if (!rel.isEmpty() && isHiddenPath(rel)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("读取技能目录失败 {}: {}", skillCode, e.getMessage());
        }
        sortTree(roots);
        return roots;
    }

    /** 列出技能目录下所有 python 脚本的相对路径 */
    public List<String> scanPythonScripts(String skillCode) {
        Path base = dir(skillCode);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String rel = rel(base, d);
                    if (!rel.isEmpty() && (isHiddenPath(rel) || IGNORE_DIRS.contains(d.getFileName().toString().toLowerCase(Locale.ROOT)))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = rel(base, file);
                    if (rel.toLowerCase(Locale.ROOT).endsWith(".py") && !isHiddenPath(rel)) {
                        result.add(rel);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("扫描脚本失败 {}: {}", skillCode, e.getMessage());
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** 统计技能目录的文件数与总大小 */
    public long[] stat(String skillCode) {
        long[] stat = {0L, 0L};
        Path base = dir(skillCode);
        if (!Files.isDirectory(base)) {
            return stat;
        }
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    stat[0]++;
                    stat[1] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 统计失败不影响主流程
        }
        return stat;
    }

    // ====================== 读写 ======================

    /** 读文本文件 */
    public String read(String skillCode, String relPath) {
        Path p = resolve(skillCode, relPath);
        if (!Files.isRegularFile(p)) {
            throw new BusinessException("文件不存在：" + relPath);
        }
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败：" + e.getMessage());
        }
    }

    /** 写文本文件（自动创建父目录） */
    public void write(String skillCode, String relPath, String content) {
        checkWritable(relPath);
        Path p = resolve(skillCode, relPath);
        long size = content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
        checkQuota(skillCode, size);
        try {
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(p, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("写入文件失败：" + e.getMessage());
        }
    }

    /** 写二进制文件（上传用） */
    public void writeBytes(String skillCode, String relPath, byte[] data) {
        checkWritable(relPath);
        Path p = resolve(skillCode, relPath);
        checkQuota(skillCode, data == null ? 0 : data.length);
        try {
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(p, data == null ? new byte[0] : data);
        } catch (IOException e) {
            throw new BusinessException("写入文件失败：" + e.getMessage());
        }
    }

    /** 删除文件或目录（递归） */
    public void delete(String skillCode, String relPath) {
        Path p = resolve(skillCode, relPath);
        try {
            if (Files.isDirectory(p)) {
                try (var walk = Files.walk(p)) {
                    List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
                    for (Path item : paths) {
                        Files.deleteIfExists(item);
                    }
                }
            } else {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new BusinessException("删除文件失败：" + e.getMessage());
        }
    }

    /** 删除整个技能目录 */
    public void deleteSkillDir(String skillCode) {
        Path p = dir(skillCode);
        if (!Files.exists(p)) {
            return;
        }
        try (var walk = Files.walk(p)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        } catch (IOException e) {
            log.warn("删除技能目录失败 {}: {}", skillCode, e.getMessage());
        }
    }

    /** 重命名技能目录（技能编码变更时跟随） */
    public void renameDir(String oldCode, String newCode) {
        if (oldCode == null || newCode == null || oldCode.equals(newCode)) {
            return;
        }
        Path from = dir(oldCode);
        Path to = dir(newCode);
        if (!Files.isDirectory(from) || Files.exists(to)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("重命名技能目录失败 {} -> {}: {}", oldCode, newCode, e.getMessage());
        }
    }

    // ====================== ZIP ======================

    /**
     * 解压 ZIP 到技能目录，自动剥离 ZIP 内统一的顶层目录
     *
     * @return 实际写入的文件相对路径列表
     */
    public List<String> extractZip(String skillCode, InputStream in) {
        Path base = ensureDir(skillCode);
        List<String> written = new ArrayList<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeZipName(entry.getName());
                // 白名单之外的文件直接丢弃，不落盘
                if (name == null || !isAllowed(name)) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = zis.read(buf)) > 0) {
                    bos.write(buf, 0, len);
                    if (bos.size() > MAX_FILE_SIZE) {
                        throw new BusinessException("压缩包内单个文件超过 5MB：" + name);
                    }
                }
                byte[] data = bos.toByteArray();
                total += data.length;
                if (total > MAX_TOTAL_SIZE) {
                    throw new BusinessException("压缩包解压后总大小超过 50MB");
                }
                Path target = base.resolve(name).normalize();
                if (!target.startsWith(base)) {
                    throw new BusinessException("压缩包内包含非法路径：" + entry.getName());
                }
                Files.createDirectories(target.getParent());
                Files.write(target, data);
                written.add(name);
            }
        } catch (IOException e) {
            throw new BusinessException("解压失败：" + e.getMessage());
        }
        if (written.size() > MAX_FILE_COUNT) {
            throw new BusinessException("文件数量超过上限 " + MAX_FILE_COUNT);
        }
        return written;
    }

    /**
     * 若技能目录下所有文件都挤在同一个子目录里，把这层目录去掉
     *
     * <p>压缩包通常是「外层目录/技能内容」的结构，直接解压会多套一层，
     * 导致 SKILL.md 与 scripts/ 藏在子目录里。</p>
     *
     * @return 被去掉的目录名，没有则返回 null
     */
    public String stripCommonTopDir(String skillCode) {
        Path base = dir(skillCode);
        if (!Files.isDirectory(base)) {
            return null;
        }
        List<Path> entries;
        try (var list = Files.list(base)) {
            entries = list.toList();
        } catch (IOException e) {
            return null;
        }
        if (entries.size() != 1 || !Files.isDirectory(entries.get(0))) {
            return null;
        }
        Path only = entries.get(0);
        String name = only.getFileName().toString();
        try {
            // 把子目录内容整体上移一层
            try (var walk = Files.walk(only)) {
                List<Path> items = walk.toList();
                for (Path item : items) {
                    if (item.equals(only)) {
                        continue;
                    }
                    Path target = base.resolve(only.relativize(item).toString());
                    if (Files.isDirectory(item)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.move(item, target);
                    }
                }
            }
            deleteQuietly(only);
            return name;
        } catch (IOException e) {
            log.warn("剥离顶层目录失败 {}: {}", skillCode, e.getMessage());
            return null;
        }
    }

    /** 递归删除目录，失败不影响主流程 */
    private void deleteQuietly(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (var walk = Files.walk(p)) {
            List<Path> items = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path item : items) {
                Files.deleteIfExists(item);
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    /** 打包整个技能目录为 ZIP 字节数组 */
    public byte[] zipSkill(String skillCode) {
        Path base = dir(skillCode);
        if (!Files.isDirectory(base)) {
            throw new BusinessException("该技能还没有文件目录");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rel = rel(base, file);
                    if (isHiddenPath(rel)) {
                        return FileVisitResult.CONTINUE;
                    }
                    ZipEntry entry = new ZipEntry(rel);
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String rel = rel(base, d);
                    if (!rel.isEmpty() && isHiddenPath(rel)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException("打包技能失败：" + e.getMessage());
        }
        return bos.toByteArray();
    }

    // ====================== 校验与工具 ======================

    private void requireCode(String skillCode) {
        if (skillCode == null || skillCode.trim().isEmpty()) {
            throw new BusinessException("技能编码不能为空");
        }
        String c = skillCode.trim();
        if (c.contains("/") || c.contains("\\") || c.contains("..")) {
            throw new BusinessException("非法的技能编码：" + skillCode);
        }
    }

    /** 扩展名与文件名白名单校验 */
    public void checkWritable(String relPath) {
        if (relPath == null || relPath.trim().isEmpty()) {
            throw new BusinessException("文件路径不能为空");
        }
        if (!isAllowed(relPath.replace('\\', '/'))) {
            throw new BusinessException("不允许写入该类型文件：" + relPath
                    + "（仅允许 " + String.join("/", ALLOWED_EXT) + " 等文本与资源文件）");
        }
    }

    /** 是否允许写入：扩展名白名单或特殊文件名 */
    public boolean isAllowed(String relPath) {
        if (relPath == null) {
            return false;
        }
        String p = relPath.replace('\\', '/').trim();
        if (p.isEmpty()) {
            return false;
        }
        String name = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
        String lower = name.toLowerCase(Locale.ROOT);
        if (ALLOWED_NAMES.contains(lower)) {
            return true;
        }
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return false;
        }
        return ALLOWED_EXT.contains(lower.substring(dot + 1));
    }

    /** 配额校验：文件数与总大小 */
    private void checkQuota(String skillCode, long incoming) {
        if (incoming > MAX_FILE_SIZE) {
            throw new BusinessException("单个文件不能超过 5MB");
        }
        long[] stat = stat(skillCode);
        if (stat[0] + 1 > MAX_FILE_COUNT) {
            throw new BusinessException("技能文件数量超过上限 " + MAX_FILE_COUNT);
        }
        if (stat[1] + incoming > MAX_TOTAL_SIZE) {
            throw new BusinessException("技能目录总大小超过上限 50MB");
        }
    }

    /** 压缩条目名归一化：去掉绝对路径、..、以及统一的顶层目录 */
    private static String normalizeZipName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace('\\', '/');
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        String[] parts = name.split("/");
        List<String> keep = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                continue;
            }
            // 丢弃 macOS 压缩包里的 __MACOSX 与隐藏文件
            if ("__MACOSX".equals(part) || part.startsWith(".")) {
                return null;
            }
            keep.add(part);
        }
        if (keep.isEmpty()) {
            return null;
        }
        return String.join("/", keep);
    }

    /** 隐藏路径：以 . 开头的目录或 __pycache__ 等 */
    private static boolean isHiddenPath(String rel) {
        if (rel == null || rel.isEmpty()) {
            return false;
        }
        for (String part : rel.split("/")) {
            if (part.startsWith(".") || IGNORE_DIRS.contains(part.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 计算相对路径（分隔符统一为 /） */
    private static String rel(Path base, Path target) {
        String r = base.relativize(target).toString().replace('\\', '/');
        return r;
    }

    /** 把扁平路径插入树 */
    private void addToTree(List<SkillFileNode> roots, String rel, boolean dir, long size, long time) {
        String[] parts = rel.split("/");
        List<SkillFileNode> current = roots;
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (pathBuilder.length() > 0) {
                pathBuilder.append('/');
            }
            pathBuilder.append(part);
            String curPath = pathBuilder.toString();
            boolean last = i == parts.length - 1;
            SkillFileNode node = findChild(current, part);
            if (node == null) {
                node = last && !dir
                        ? SkillFileNode.file(part, curPath, size, time)
                        : SkillFileNode.directory(part, curPath, time);
                current.add(node);
            }
            current = node.getChildren();
        }
    }

    private SkillFileNode findChild(List<SkillFileNode> list, String name) {
        for (SkillFileNode n : list) {
            if (n.getName().equals(name)) {
                return n;
            }
        }
        return null;
    }

    /** 排序：目录在前，其余按名称 */
    private void sortTree(List<SkillFileNode> nodes) {
        nodes.sort(Comparator.comparing((SkillFileNode n) -> Boolean.TRUE.equals(n.getDir()) ? 0 : 1)
                .thenComparing(SkillFileNode::getName));
        for (SkillFileNode n : nodes) {
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                sortTree(n.getChildren());
            }
        }
    }

    /** 写出到输出流，供下载使用 */
    public void writeTo(Path file, OutputStream out) throws IOException {
        Files.copy(file, out);
    }
}
