package com.aip.platform.knowledge.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量分词：英文/数字按词，中文按 2-gram
 *
 * <p>不引入分词器依赖（IK/ansj 等），对知识检索这种「召回 + 重排」场景，
 * 二元切分已经能覆盖绝大部分中文匹配，且不会因为词典缺失导致召回为零。</p>
 */
@Slf4j
@Component
public class KeywordTokenizer {

    /** 高频无意义词，不参与召回 */
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很",
            "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "么", "吧", "呢",
            "啊", "可以", "什么", "怎么", "为什么", "请", "帮", "帮我", "一下", "如何", "哪些", "多少",
            "the", "a", "an", "is", "are", "to", "of", "and", "or", "for", "in", "on", "it", "this", "that");

    /** 召回词数量上限，SQL 里 LIKE 条件过多会拖慢查询 */
    private static final int MAX_TERMS = 24;

    /**
     * 切词
     *
     * @param text 查询文本
     * @return 去重后的关键词（长词优先）
     */
    public List<String> tokenize(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String s = text.toLowerCase(Locale.ROOT);

        // 英文/数字词
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            boolean keep = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (keep) {
                word.append(c);
            } else {
                if (word.length() >= 2 && !STOPWORDS.contains(word.toString())) {
                    out.add(word.toString());
                }
                word.setLength(0);
            }
        }

        // 中文 2-gram（连续的中文片段内滑窗）
        StringBuilder cn = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            if (isChinese(c)) {
                cn.append(c);
            } else {
                collectChinese(out, cn.toString());
                cn.setLength(0);
            }
        }
        out.removeIf(STOPWORDS::contains);

        // 长词优先：更长的词区分度更高
        return out.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .limit(MAX_TERMS)
                .toList();
    }

    private void collectChinese(Set<String> out, String seg) {
        if (seg.length() < 2) {
            if (seg.length() == 1 && !STOPWORDS.contains(seg)) {
                out.add(seg);
            }
            return;
        }
        for (int i = 0; i + 2 <= seg.length(); i++) {
            String t = seg.substring(i, i + 2);
            if (!STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }
}
