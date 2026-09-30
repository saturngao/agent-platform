package com.aip.platform.work.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 Markdown -> HTML 转换器（零第三方依赖）
 *
 * <p>仅覆盖 AI 总结报告实际会用到的语法：
 * 标题（# / ## / ### / ####）、无序列表（- * +）、有序列表（1. 2.）、
 * 代码块（```lang）、行内代码（`code`）、粗体（**text**）、链接 [text](url)、
 * 引用（&gt;）、空行分段、段落内换行转 &lt;br/&gt;。</p>
 *
 * <p>所有输入均先做 HTML 转义再生成标签，避免 XSS。</p>
 */
public final class MiniMarkdown {

    /** 有序列表：1. / 1、 / 1) 开头 */
    private static final Pattern ORDERED = Pattern.compile("^\\d+[.、)]\\s+(.*)$");

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)([^*\\n]+)\\*(?!\\*)");

    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

    private MiniMarkdown() {
    }

    /**
     * 将 Markdown 文本转换为 HTML 片段
     *
     * @param markdown Markdown 原文
     * @return HTML 片段，入参为空时返回空串
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);

        StringBuilder html = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        List<String> ulItems = new ArrayList<>();
        List<String> olItems = new ArrayList<>();
        StringBuilder code = new StringBuilder();
        String codeLang = "";
        boolean inCode = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();

            // 代码块围栏
            if (line.startsWith("```")) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                flushList(html, "ol", olItems);
                if (inCode) {
                    html.append("<pre><code");
                    if (!codeLang.isEmpty()) {
                        html.append(" class=\"language-").append(escapeHtml(codeLang)).append("\"");
                    }
                    html.append(">").append(escapeHtml(code.toString())).append("</code></pre>\n");
                    inCode = false;
                    code.setLength(0);
                    codeLang = "";
                } else {
                    inCode = true;
                    codeLang = line.substring(3).trim();
                }
                continue;
            }
            if (inCode) {
                if (code.length() > 0) {
                    code.append('\n');
                }
                code.append(raw == null ? "" : raw);
                continue;
            }

            // 空行：结束当前段落与列表
            if (line.isEmpty()) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                flushList(html, "ol", olItems);
                continue;
            }

            // 标题
            int level = headingLevel(line);
            if (level > 0) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                flushList(html, "ol", olItems);
                html.append("<h").append(level).append(">")
                        .append(inline(line.substring(level).trim()))
                        .append("</h").append(level).append(">\n");
                continue;
            }

            // 无序列表
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                flushParagraph(html, paragraph);
                flushList(html, "ol", olItems);
                ulItems.add(inline(line.substring(2).trim()));
                continue;
            }

            // 有序列表
            Matcher matcher = ORDERED.matcher(line);
            if (matcher.matches()) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                olItems.add(inline(matcher.group(1).trim()));
                continue;
            }

            // 引用
            if (line.startsWith("> ")) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                flushList(html, "ol", olItems);
                html.append("<blockquote>").append(inline(line.substring(2).trim()))
                        .append("</blockquote>\n");
                continue;
            }

            // 分隔线
            if ("---".equals(line) || "***".equals(line)) {
                flushParagraph(html, paragraph);
                flushList(html, "ul", ulItems);
                flushList(html, "ol", olItems);
                html.append("<hr/>\n");
                continue;
            }

            // 普通段落
            flushList(html, "ul", ulItems);
            flushList(html, "ol", olItems);
            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }

        // 收尾：未闭合的段落、列表、代码块
        flushParagraph(html, paragraph);
        flushList(html, "ul", ulItems);
        flushList(html, "ol", olItems);
        if (inCode) {
            html.append("<pre><code>").append(escapeHtml(code.toString())).append("</code></pre>\n");
        }
        return html.toString();
    }

    /** 统计标题层级，非标题返回 0 */
    private static int headingLevel(String line) {
        int i = 0;
        while (i < line.length() && i < 6 && line.charAt(i) == '#') {
            i++;
        }
        return i > 0 ? i : 0;
    }

    /** 输出段落 */
    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        html.append("<p>").append(inline(paragraph.toString()).replace("\n", "<br/>")).append("</p>\n");
        paragraph.setLength(0);
    }

    /** 输出列表 */
    private static void flushList(StringBuilder html, String tag, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        html.append("<").append(tag).append(">");
        for (String item : items) {
            html.append("<li>").append(item).append("</li>");
        }
        html.append("</").append(tag).append(">\n");
        items.clear();
    }

    /** 行内语法：先转义再还原富文本 */
    private static String inline(String text) {
        String escaped = escapeHtml(text);
        escaped = INLINE_CODE.matcher(escaped).replaceAll("<code>$1</code>");
        escaped = LINK.matcher(escaped).replaceAll("<a href=\"$2\" target=\"_blank\">$1</a>");
        escaped = BOLD.matcher(escaped).replaceAll("<strong>$1</strong>");
        escaped = ITALIC.matcher(escaped).replaceAll("<em>$1</em>");
        return escaped;
    }

    /** HTML 转义，防止注入 */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
