package com.aip.platform.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CSV / TSV 解析
 *
 * <p>把表格转成「表头: 值」形式的文本行，保证检索时语义完整——
 * 否则单独一个单元格被召回后无法判断它属于哪一列。</p>
 */
@Slf4j
@Component
public class CsvParser implements DocumentParser {

    private static final Set<String> EXTS = Set.of("csv", "tsv");

    /** 超过该列数时按纯文本处理，避免超宽表生成无意义长行 */
    private static final int MAX_COLS = 60;

    @Override
    public boolean supports(String ext) {
        return EXTS.contains(ext);
    }

    @Override
    public ParsedDocument parse(InputStream in, String fileName) throws Exception {
        byte[] bytes = in.readAllBytes();
        String text = TextExtractUtil.stripBom(TextExtractUtil.decode(bytes));
        char sep = "tsv".equalsIgnoreCase(extOf(fileName)) ? '\t' : ',';

        List<String[]> rows = readCsv(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), sep);
        if (rows.isEmpty()) {
            return ParsedDocument.builder().text("").title(fileName).build();
        }

        String[] header = rows.get(0);
        boolean hasHeader = header.length > 0 && !isAllNumeric(header);
        StringBuilder sb = new StringBuilder();
        if (hasHeader) {
            sb.append(String.join(" | ", header)).append('\n');
        }
        int limit = Math.min(rows.size(), hasHeader ? rows.size() : rows.size());
        for (int i = hasHeader ? 1 : 0; i < limit; i++) {
            String[] row = rows.get(i);
            if (row.length > MAX_COLS) {
                sb.append(String.join(" | ", row)).append('\n');
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.length; c++) {
                String col = hasHeader && c < header.length ? header[c] : ("列" + (c + 1));
                line.append(col).append(": ").append(row[c]);
                if (c < row.length - 1) {
                    line.append("；");
                }
            }
            sb.append(line).append('\n');
        }
        return ParsedDocument.builder()
                .text(TextExtractUtil.compact(sb.toString()))
                .title(fileName)
                .pageCount(rows.size())
                .build();
    }

    private boolean isAllNumeric(String[] row) {
        for (String v : row) {
            if (v != null && !v.trim().isEmpty() && !v.trim().matches("-?\\d+(\\.\\d+)?")) {
                return false;
            }
        }
        return true;
    }

    /** 简易 RFC4180 解析：支持引号包裹、转义双引号、内嵌换行 */
    private List<String[]> readCsv(InputStream in, char sep) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder cell = new StringBuilder();
            List<String> row = new ArrayList<>();
            boolean inQuotes = false;
            int c;
            while ((c = br.read()) != -1) {
                char ch = (char) c;
                if (inQuotes) {
                    if (ch == '"') {
                        int next = br.read();
                        if (next == '"') {
                            cell.append('"');
                        } else {
                            inQuotes = false;
                            if (next == -1) {
                                row.add(cell.toString());
                                rows.add(row.toArray(new String[0]));
                                row.clear();
                                cell.setLength(0);
                            } else if (next == sep) {
                                row.add(cell.toString());
                                cell.setLength(0);
                            } else if (next == '\n' || next == '\r') {
                                row.add(cell.toString());
                                rows.add(row.toArray(new String[0]));
                                row.clear();
                                cell.setLength(0);
                            } else {
                                cell.append((char) next);
                            }
                        }
                    } else {
                        cell.append(ch);
                    }
                    continue;
                }
                if (ch == '"' && cell.length() == 0) {
                    inQuotes = true;
                } else if (ch == sep) {
                    row.add(cell.toString());
                    cell.setLength(0);
                } else if (ch == '\n') {
                    row.add(cell.toString());
                    rows.add(row.toArray(new String[0]));
                    row.clear();
                    cell.setLength(0);
                } else if (ch != '\r') {
                    cell.append(ch);
                }
            }
            if (cell.length() > 0 || !row.isEmpty()) {
                row.add(cell.toString());
                rows.add(row.toArray(new String[0]));
            }
        }
        // 丢弃全空行
        rows.removeIf(r -> r.length == 0 || (r.length == 1 && r[0].isBlank()));
        return rows;
    }

    private String extOf(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase();
    }
}
