package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分片器
 *
 * <p>策略：先按空行/换行切成自然段落，再把过长的段落按句子边界二次切分；
 * 相邻分片保留 {@code overlap} 个字符的重叠，避免语义被切断在边界上。</p>
 */
@Slf4j
@Component
public class TextSplitter {

    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int size = chunkSize <= 0 ? 500 : chunkSize;
        int ov = Math.min(overlap <= 0 ? 0 : overlap, size / 2);

        for (String para : splitParagraphs(text)) {
            if (para.length() <= size) {
                append(chunks, para, size, ov);
                continue;
            }
            // 长段落按句子再切
            StringBuilder buf = new StringBuilder();
            for (String sentence : splitSentences(para)) {
                if (buf.length() + sentence.length() > size && buf.length() > 0) {
                    append(chunks, buf.toString(), size, ov);
                    keepTail(buf, ov);
                }
                buf.append(sentence);
                if (buf.length() > size) {
                    append(chunks, buf.toString(), size, ov);
                    keepTail(buf, ov);
                }
            }
            if (buf.length() > 0) {
                append(chunks, buf.toString(), size, ov);
            }
        }
        return chunks;
    }

    private void append(List<String> chunks, String piece, int size, int ov) {
        String t = piece.trim();
        if (t.isEmpty()) {
            return;
        }
        // 超长硬切（无句子边界的长串）
        int start = 0;
        while (t.length() - start > size) {
            String part = t.substring(start, start + size);
            chunks.add(part);
            start += size - (ov > 0 ? ov : 0);
        }
        String tail = t.substring(start);
        // 与上一段尾部合并，减少碎片
        if (!chunks.isEmpty() && tail.length() < size / 4 && chunks.get(chunks.size() - 1).length() + tail.length() + 1 <= size) {
            chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + "\n" + tail);
        } else if (!tail.isEmpty()) {
            chunks.add(tail);
        }
    }

    private void keepTail(StringBuilder buf, int ov) {
        if (ov <= 0 || buf.length() <= ov) {
            return;
        }
        String tail = buf.substring(buf.length() - ov);
        buf.setLength(0);
        buf.append(tail);
    }

    private List<String> splitParagraphs(String text) {
        List<String> list = new ArrayList<>();
        String[] parts = text.replace("\r\n", "\n").split("\n{2,}");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        if (list.isEmpty()) {
            for (String line : text.split("\n")) {
                if (!line.isBlank()) {
                    list.add(line.trim());
                }
            }
        }
        return list;
    }

    private List<String> splitSentences(String para) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (char c : para.toCharArray()) {
            buf.append(c);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；' || c == '.' || c == '!' || c == '?') {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out.isEmpty() ? List.of(para) : out;
    }
}
