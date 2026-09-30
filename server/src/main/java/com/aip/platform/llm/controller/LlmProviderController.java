package com.aip.platform.llm.controller;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.llm.entity.LlmProvider;
import com.aip.platform.llm.mapper.LlmProviderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
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

/**
 * 大模型供应商接口
 */
@RestController
@RequestMapping("/api/llm/provider")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderMapper providerMapper;

    @GetMapping({"", "/list"})
    public R<PageResult<LlmProvider>> list(LlmProvider params, PageQuery query) {
        LambdaQueryWrapper<LlmProvider> wrapper = new LambdaQueryWrapper<>();
        if (params != null) {
            if (params.getProviderName() != null && !params.getProviderName().isEmpty()) {
                wrapper.like(LlmProvider::getProviderName, params.getProviderName());
            }
            if (params.getStatus() != null) {
                wrapper.eq(LlmProvider::getStatus, params.getStatus());
            }
        }
        wrapper.orderByAsc(LlmProvider::getSortNum);
        IPage<LlmProvider> page = providerMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/optionselect")
    public R<List<LlmProvider>> optionselect() {
        LambdaQueryWrapper<LlmProvider> wrapper = new LambdaQueryWrapper<LlmProvider>()
                .eq(LlmProvider::getStatus, 0)
                .orderByAsc(LlmProvider::getSortNum);
        return R.ok(providerMapper.selectList(wrapper));
    }

    @GetMapping("/{providerId}")
    public R<LlmProvider> detail(@PathVariable("providerId") Long providerId) {
        return R.ok(providerMapper.selectById(providerId));
    }

    @PreAuthorize("@permissionChecker.has('llm:model:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody LlmProvider provider) {
        providerMapper.insert(provider);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('llm:model:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody LlmProvider provider) {
        providerMapper.updateById(provider);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('llm:model:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        providerMapper.deleteByIds(ids);
        return R.ok();
    }
}
