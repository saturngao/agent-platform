package com.aip.platform.knowledge.parser;

import java.io.InputStream;

/**
 * 文档解析器
 */
public interface DocumentParser {

    /**
     * 是否支持该扩展名
     *
     * @param ext 小写扩展名，不含点
     */
    boolean supports(String ext);

    /**
     * 解析输入流为纯文本
     */
    ParsedDocument parse(InputStream in, String fileName) throws Exception;
}
