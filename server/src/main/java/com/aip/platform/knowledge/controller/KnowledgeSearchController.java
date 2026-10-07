package com.aip.platform.knowledge.controller;

import com.aip.platform.common.BusinessException;
import com.aip.platform.common.R;
import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.aip.platform.knowledge.service.IKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识检索接口：页面测试检索用，同时也被工作流 / 智能体在内部直接调用服务
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/search")
@RequiredArgsConstructor
public class KnowledgeSearchController {

    private final IKnowledgeSearchService searchService;

    /** 简单查询：GET /api/knowledge/search?baseId=1&query=xxx&topK=5 */
    @GetMapping({"", "/hit"})
    public R<List<KnowledgeChunk>> hit(@RequestParam("baseId") Long baseId,
                                       @RequestParam("query") String query,
                                       @RequestParam(value = "topK", required = false) Integer topK) {
        return R.ok(searchService.search(baseId, query, topK));
    }

    /**
     * 复杂查询：POST body { baseIds: [1,2], query: "xxx", topK: 5 }
     */
    @PostMapping({"", "/query"})
    public R<List<KnowledgeChunk>> query(@RequestBody Map<String, Object> body) {
        List<Long> baseIds = toLongList(body.get("baseIds"));
        if (baseIds == null || baseIds.isEmpty()) {
            Long one = toLong(body.get("baseId"));
            if (one == null) {
                throw new BusinessException("请指定知识库 baseId 或 baseIds");
            }
            baseIds = List.of(one);
        }
        String query = body.get("query") == null ? null : String.valueOf(body.get("query"));
        Integer topK = toLong(body.get("topK")) == null ? null : toLong(body.get("topK")).intValue();
        if (topK == null && body.get("topK") instanceof Number n) {
            topK = n.intValue();
        }
        return R.ok(searchService.searchMulti(baseIds, query, topK));
    }

    private List<Long> toLongList(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().map(this::toLong).filter(java.util.Objects::nonNull).toList();
        }
        Long one = toLong(v);
        return one == null ? List.of() : List.of(one);
    }

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
