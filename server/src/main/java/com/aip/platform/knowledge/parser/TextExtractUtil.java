package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文本抽取通用工具：编码探测、HTML 去标签、空白折叠
 */
@Slf4j
public final class TextExtractUtil {

    private TextExtractUtil() {
    }

    /**
     * 读取流并按 UTF-8 / GBK 智能解码
     *
     * <p>先按 UTF-8 解码，若出现大量替换字符则回退 GBK（国内 Windows 导出的 txt/csv 常见）。</p>
     */
    public static String readText(InputStream in) throws Exception {
        byte[] bytes = in.readAllBytes();
        return decode(bytes);
    }

    public static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        try {
            // GBK 无法表示的部分会变成 '?'，取替换字符更少的那个
            String gbk = new String(bytes, Charset.forName("GBK"));
            return countOf(utf8, '\uFFFD') > countOf(gbk, '?') ? gbk : utf8;
        } catch (Exception e) {
            return utf8;
        }
    }

    private static int countOf(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /** 去掉 BOM */
    public static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    /** 简易去除 HTML 标签（无 jsoup 依赖，够用于正文抽取） */
    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html;
        // 先去掉脚本/样式/注释块
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        // 块级标签换成换行
        s = s.replaceAll("(?i)</?(p|div|br|tr|li|h[1-6]|table|section|article)[^>]*>", "\n");
        s = s.replaceAll("(?i)</t[dh]>", "\t");
        // 其余标签丢弃
        s = s.replaceAll("(?s)<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'");
        return compact(s);
    }

    /** 折叠多余空白与空行 */
    public static String compact(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("[ \t\u00A0]+", " ");
        s = s.replaceAll("\n{3,}", "\n\n");
        s = s.replaceAll("(?m)^[ \t]+|[ \t]+$", "");
        return s.trim();
    }
}
