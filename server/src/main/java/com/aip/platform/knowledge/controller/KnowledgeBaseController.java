package com.aip.platform.knowledge.controller;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.knowledge.entity.KnowledgeBase;
import com.aip.platform.knowledge.service.IKnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final IKnowledgeBaseService baseService;

    @GetMapping({"", "/list"})
    public R<PageResult<KnowledgeBase>> list(KnowledgeBase q, PageQuery query) {
        return R.ok(baseService.selectPage(query, q));
    }

    /** 下拉选项：工作流节点 / 智能体绑定知识库 */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(baseService.selectOptions());
    }

    @GetMapping("/{baseId}")
    public R<KnowledgeBase> detail(@PathVariable("baseId") Long baseId) {
        return R.ok(baseService.selectDetail(baseId));
    }

    @PreAuthorize("@permissionChecker.has('knowledge:base:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody KnowledgeBase base) {
        baseService.insertBase(base);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('knowledge:base:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody KnowledgeBase base) {
        baseService.updateBase(base);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('knowledge:base:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        baseService.deleteBases(ids);
        return R.ok();
    }
}
