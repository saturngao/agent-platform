package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Office 解析（Apache POI）
 *
 * <p>覆盖 doc / docx / xls / xlsx / ppt / pptx。
 * Word：段落 + 表格；Excel：按 sheet 输出表头与行；PPT：按页输出文本。</p>
 */
@Slf4j
@Component
public class OfficeParser implements DocumentParser {

    private static final Set<String> WORD = Set.of("doc", "docx");
    private static final Set<String> EXCEL = Set.of("xls", "xlsx");
    private static final Set<String> PPT = Set.of("ppt", "pptx");

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Override
    public boolean supports(String ext) {
        return WORD.contains(ext) || EXCEL.contains(ext) || PPT.contains(ext);
    }

    @Override
    public ParsedDocument parse(InputStream in, String fileName) throws Exception {
        byte[] bytes = in.readAllBytes();
        String ext = extOf(fileName);
        if (WORD.contains(ext)) {
            return "docx".equals(ext) ? readDocx(bytes, fileName) : readDoc(bytes, fileName);
        }
        if (EXCEL.contains(ext)) {
            return readExcel(bytes, fileName);
        }
        return "pptx".equals(ext) ? readPptx(bytes, fileName) : readPpt(bytes, fileName);
    }

    // ---------------------------- Word ----------------------------

    private ParsedDocument readDocx(byte[] bytes, String fileName) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    sb.append(t.trim()).append('\n');
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String t = cell.getText();
                        if (t != null) {
                            line.append(t.trim()).append(" | ");
                        }
                    }
                    if (line.length() > 0) {
                        sb.append(line.substring(0, Math.max(0, line.length() - 3))).append('\n');
                    }
                }
            }
            String title = doc.getProperties() == null || doc.getProperties().getCoreProperties() == null
                    ? fileName : doc.getProperties().getCoreProperties().getTitle();
            return ParsedDocument.builder()
                    .text(TextExtractUtil.compact(sb.toString()))
                    .title(blankToNull(title, fileName))
                    .build();
        }
    }

    private ParsedDocument readDoc(byte[] bytes, String fileName) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new java.io.ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(doc)) {
            String[] paragraphs = extractor.getParagraphText();
            StringBuilder sb = new StringBuilder();
            for (String p : paragraphs) {
                if (p != null && !p.isBlank()) {
                    sb.append(p.trim()).append('\n');
                }
            }
            String text = TextExtractUtil.compact(sb.toString());
            if (text.isBlank()) {
                // 部分 doc 正文在表格里
                text = TextExtractUtil.compact(extractor.getText());
            }
            return ParsedDocument.builder().text(text).title(fileName).build();
        }
    }

    // ---------------------------- Excel ----------------------------

    private ParsedDocument readExcel(byte[] bytes, String fileName) throws Exception {
        try (Workbook wb = newWorkbook(bytes, fileName)) {
            StringBuilder sb = new StringBuilder();
            int sheets = wb.getNumberOfSheets();
            for (int s = 0; s < sheets; s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                sb.append("## 工作表：").append(sheet.getSheetName()).append('\n');
                String[] header = null;
                int rows = 0;
                for (Row row : sheet) {
                    if (isEmptyRow(row)) {
                        continue;
                    }
                    String[] values = rowValues(row);
                    if (header == null) {
                        header = values;
                        sb.append(String.join(" | ", values)).append('\n');
                        continue;
                    }
                    StringBuilder line = new StringBuilder();
                    for (int c = 0; c < values.length; c++) {
                        String col = c < header.length && !header[c].isBlank() ? header[c] : ("列" + (c + 1));
                        line.append(col).append(": ").append(values[c]);
                        if (c < values.length - 1) {
                            line.append("；");
                        }
                    }
                    sb.append(line).append('\n');
                    if (++rows > 20000) {
                        sb.append("（后续行已省略）\n");
                        break;
                    }
                }
                sb.append('\n');
            }
            return ParsedDocument.builder()
                    .text(TextExtractUtil.compact(sb.toString()))
                    .title(fileName)
                    .pageCount(sheets)
                    .build();
        }
    }

    private Workbook newWorkbook(byte[] bytes, String fileName) throws Exception {
        return "xlsx".equals(extOf(fileName))
                ? new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))
                : new HSSFWorkbook(new java.io.ByteArrayInputStream(bytes));
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !FORMATTER.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String[] rowValues(Row row) {
        short last = row.getLastCellNum();
        String[] values = new String[Math.max(last, 0)];
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.getCell(c);
            values[c] = cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
        }
        return values;
    }

    // ---------------------------- PowerPoint ----------------------------

    private ParsedDocument readPptx(byte[] bytes, String fileName) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            List<XSLFSlide> slides = ppt.getSlides();
            int i = 1;
            for (XSLFSlide slide : slides) {
                sb.append("## 第 ").append(i++).append(" 页\n");
                if (slide.getTitle() != null && !slide.getTitle().isBlank()) {
                    sb.append("标题：").append(slide.getTitle().trim()).append('\n');
                }
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t.trim()).append('\n');
                        }
                    }
                });
                sb.append('\n');
            }
            return ParsedDocument.builder()
                    .text(TextExtractUtil.compact(sb.toString()))
                    .title(fileName)
                    .pageCount(slides.size())
                    .build();
        }
    }

    private ParsedDocument readPpt(byte[] bytes, String fileName) throws Exception {
        try (HSLFSlideShow ppt = new HSLFSlideShow(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (org.apache.poi.hslf.usermodel.HSLFSlide slide : ppt.getSlides()) {
                sb.append("## 第 ").append(i++).append(" 页\n");
                String title = slide.getTitle();
                if (title != null && !title.isBlank()) {
                    sb.append("标题：").append(title.trim()).append('\n');
                }
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t.trim()).append('\n');
                        }
                    }
                }
                sb.append('\n');
            }
            return ParsedDocument.builder()
                    .text(TextExtractUtil.compact(sb.toString()))
                    .title(fileName)
                    .pageCount(ppt.getSlides().size())
                    .build();
        }
    }

    private String blankToNull(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private String extOf(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase();
    }
}
