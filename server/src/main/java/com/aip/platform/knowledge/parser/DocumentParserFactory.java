package com.aip.platform.knowledge.parser;

import com.aip.platform.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * 文档解析器工厂：按扩展名选择解析器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    /** 支持上传的扩展名（小写，含点） */
    public static final List<String> SUPPORTED_EXT = List.of(
            ".txt", ".md", ".markdown", ".log", ".json", ".jsonl", ".yaml", ".yml", ".xml", ".sql",
            ".html", ".htm", ".csv", ".tsv",
            ".pdf",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx");

    /** 单文件最大 50MB */
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /**
     * 解析文档
     *
     * @param in       输入流
     * @param fileName 原始文件名（用于取扩展名）
     */
    public ParsedDocument parse(InputStream in, String fileName) {
        String ext = extOf(fileName);
        if (!SUPPORTED_EXT.contains("." + ext)) {
            throw new BusinessException("暂不支持的文件类型：" + (ext.isEmpty() ? "（无扩展名）" : ext)
                    + "，支持 " + String.join("、", SUPPORTED_EXT));
        }
        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(ext))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到可用的解析器：" + ext));
        try {
            return parser.parse(in, fileName);
        } catch (Exception e) {
            log.error("[知识库] 解析文档失败 file={}", fileName, e);
            throw new BusinessException("解析失败：" + shortMsg(e));
        }
    }

    public static String extOf(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
