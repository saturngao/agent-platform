package com.aip.platform.knowledge.controller;

import com.aip.platform.common.BusinessException;
import com.aip.platform.common.R;
import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.aip.platform.knowledge.entity.KnowledgeDocument;
import com.aip.platform.knowledge.parser.DocumentParserFactory;
import com.aip.platform.knowledge.service.IKnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档接口：上传 / 解析 / 分片 / 向量
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/doc")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final IKnowledgeDocumentService docService;

    /** 某知识库下的文档列表 */
    @GetMapping({"", "/list"})
    public R<List<KnowledgeDocument>> list(@RequestParam("baseId") Long baseId) {
        return R.ok(docService.selectByBaseId(baseId));
    }

    @GetMapping("/{docId}")
    public R<KnowledgeDocument> detail(@PathVariable("docId") Long docId) {
        return R.ok(docService.selectDetail(docId));
    }

    /** 支持的文件类型（前端上传控件 accept 用） */
    @GetMapping("/supported")
    public R<Map<String, Object>> supported() {
        Map<String, Object> m = new HashMap<>(4);
        m.put("ext", DocumentParserFactory.SUPPORTED_EXT);
        m.put("accept", String.join(",", DocumentParserFactory.SUPPORTED_EXT));
        m.put("maxSizeMb", DocumentParserFactory.MAX_FILE_SIZE / 1024 / 1024);
        return R.ok(m);
    }

    /**
     * 上传文档（支持多选，逐个解析）
     *
     * <p>单个失败不影响其它文件，返回每个文件的结果明细。</p>
     */
    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @PostMapping("/upload")
    public R<List<Map<String, Object>>> upload(@RequestParam("baseId") Long baseId,
                                               @RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> item = new HashMap<>(6);
            item.put("name", file.getOriginalFilename());
            try {
                KnowledgeDocument doc = docService.upload(baseId, file);
                item.put("ok", true);
                item.put("docId", doc.getDocId());
                item.put("chunkCount", doc.getChunkCount());
                item.put("charCount", doc.getCharCount());
                item.put("msg", doc.getParseMsg());
            } catch (Exception e) {
                item.put("ok", false);
                item.put("msg", e.getMessage());
            }
            result.add(item);
        }
        return R.ok(result);
    }

    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @PostMapping("/reparse/{docId}")
    public R<KnowledgeDocument> reparse(@PathVariable("docId") Long docId) {
        return R.ok(docService.reparse(docId));
    }

    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @PutMapping("/status/{docId}")
    public R<Void> changeStatus(@PathVariable("docId") Long docId, @RequestParam("status") Integer status) {
        docService.changeStatus(docId, status);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        docService.deleteDocs(ids);
        return R.ok();
    }

    /** 文档分片预览 */
    @GetMapping("/chunks/{docId}")
    public R<List<KnowledgeChunk>> chunks(@PathVariable("docId") Long docId) {
        return R.ok(docService.selectChunks(docId));
    }

    /** 为知识库补生成向量 */
    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @PostMapping("/embedding/build")
    public R<Map<String, Object>> buildEmbedding(@RequestParam("baseId") Long baseId) {
        int n = docService.buildEmbedding(baseId);
        Map<String, Object> m = new HashMap<>(2);
        m.put("count", n);
        return R.ok("已生成 " + n + " 条向量", m);
    }

    /** 清空知识库向量（切换 embedding 模型后用） */
    @PreAuthorize("@permissionChecker.has('knowledge:doc:edit')")
    @PostMapping("/embedding/clear")
    public R<Map<String, Object>> clearEmbedding(@RequestParam("baseId") Long baseId) {
        int n = docService.clearEmbedding(baseId);
        Map<String, Object> m = new HashMap<>(2);
        m.put("count", n);
        return R.ok("已清除 " + n + " 条向量", m);
    }
}
