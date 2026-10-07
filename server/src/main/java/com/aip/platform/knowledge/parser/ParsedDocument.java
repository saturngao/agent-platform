package com.aip.platform.knowledge.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    /** 抽取出的纯文本 */
    private String text;

    /** 文档标题（PDF 元数据 / Word 标题 / 文件名兜底） */
    private String title;

    /** 页数或工作表数等结构化信息，仅用于展示 */
    private Integer pageCount;
}
