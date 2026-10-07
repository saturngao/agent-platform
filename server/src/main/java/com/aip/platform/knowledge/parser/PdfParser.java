package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * PDF 解析（PDFBox 2.x）
 *
 * <p>只抽取文本层；扫描件（图片型 PDF）没有文本层，会解析出空内容，
 * 此时由上层标记为「解析失败/无文本内容」并提示用户。</p>
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "pdf".equals(ext);
    }

    @Override
    public ParsedDocument parse(InputStream in, String fileName) throws Exception {
        try (PDDocument doc = PDDocument.load(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            int pages = doc.getNumberOfPages();
            stripper.setEndPage(pages);
            String text = TextExtractUtil.compact(stripper.getText(doc));
            String title = doc.getDocumentInformation() == null ? null : doc.getDocumentInformation().getTitle();
            if (title == null || title.isBlank()) {
                title = fileName;
            }
            return ParsedDocument.builder().text(text).title(title).pageCount(pages).build();
        }
    }
}
