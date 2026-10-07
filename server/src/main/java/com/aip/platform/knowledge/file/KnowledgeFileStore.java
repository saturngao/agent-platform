package com.aip.platform.knowledge.file;

import com.aip.platform.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 知识库文档落盘管理
 *
 * <p>目录结构：{@code <root>/<baseCode>/<uuid>_<原文件名>}，
 * 文件名加 uuid 前缀避免同名覆盖与中文名乱码问题（原文件名存数据库用于展示）。</p>
 */
@Slf4j
@Component
public class KnowledgeFileStore {

    private final Path root;

    public KnowledgeFileStore(@Value("${platform.knowledge.dir:./data/knowledge}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            log.error("[知识库] 创建存储目录失败：{}", this.root, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    /** 知识库目录 */
    public Path baseDir(String baseCode) {
        Path dir = root.resolve(safe(baseCode)).normalize();
        if (!dir.startsWith(root)) {
            throw new BusinessException("非法的知识库编码");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建知识库目录失败：" + e.getMessage());
        }
        return dir;
    }

    /**
     * 保存上传文件
     *
     * @return 相对知识库根目录的存储路径（入库用）
     */
    public String save(String baseCode, String originalName, InputStream in) {
        String safeName = safeFileName(originalName);
        String storeName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        Path target = baseDir(baseCode).resolve(storeName);
        try (in) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("保存文件失败：" + e.getMessage());
        }
        return safe(baseCode) + "/" + storeName;
    }

    /** 解析相对路径为绝对路径（带目录穿越防护） */
    public Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new BusinessException("非法的文件路径");
        }
        return p;
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("[知识库] 删除文件失败：{}", relativePath, e);
        }
    }

    public void deleteBaseDir(String baseCode) {
        try {
            Files.deleteIfExists(baseDir(baseCode));
        } catch (Exception e) {
            log.warn("[知识库] 删除知识库目录失败：{}", baseCode, e);
        }
    }

    private String safe(String code) {
        String s = code == null ? "default" : code.trim();
        if (s.isEmpty() || s.contains("..") || s.contains("/") || s.contains("\\")) {
            throw new BusinessException("非法的知识库编码：" + code);
        }
        return s;
    }

    private String safeFileName(String name) {
        String n = name == null ? "unnamed" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 ? n.substring(dot) : "";
            n = n.substring(0, 120 - ext.length()) + ext;
        }
        return n.isEmpty() ? "unnamed" : n;
    }
}
