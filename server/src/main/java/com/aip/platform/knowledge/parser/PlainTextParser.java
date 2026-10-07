package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

/**
 * 纯文本类解析：txt / md / markdown / log / json / yaml / yml / sql / xml / html / htm / properties / ini
 */
@Slf4j
@Component
public class PlainTextParser implements DocumentParser {

    private static final Set<String> EXTS = Set.of(
            "txt", "text", "md", "markdown", "log", "json", "jsonl", "yaml", "yml",
            "sql", "xml", "properties", "ini", "conf", "csv2", "rtf");

    private static final Set<String> HTML_EXTS = Set.of("html", "htm");

    @Override
    public boolean supports(String ext) {
        return EXTS.contains(ext) || HTML_EXTS.contains(ext);
    }

    @Override
    public ParsedDocument parse(InputStream in, String fileName) throws Exception {
        String raw = TextExtractUtil.stripBom(TextExtractUtil.readText(in));
        String text = HTML_EXTS.contains(extOf(fileName)) ? TextExtractUtil.stripHtml(raw) : TextExtractUtil.compact(raw);
        String title = firstLine(text);
        return ParsedDocument.builder().text(text).title(title).pageCount(null).build();
    }

    private String extOf(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase();
    }

    private String firstLine(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > 100 ? t.substring(0, 100) : t;
            }
        }
        return null;
    }
}
