package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.DictType;
import com.aip.platform.system.service.ISysDictService;
import com.aip.platform.system.vo.PageParam;
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
 * 字典类型管理接口
 */
@RestController
@RequestMapping("/api/system/dict/type")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final ISysDictService dictService;

    /**
     * 字典类型列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<DictType>> list(DictType dictType, PageParam query) {
        return R.ok(dictService.selectDictTypePage(query, dictType));
    }

    /**
     * 新增字典类型
     */
    @PreAuthorize("@permissionChecker.has('system:dict:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody DictType dictType) {
        dictService.insertDictType(dictType);
        return R.ok();
    }

    /**
     * 修改字典类型
     */
    @PreAuthorize("@permissionChecker.has('system:dict:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody DictType dictType) {
        dictService.updateDictType(dictType);
        return R.ok();
    }

    /**
     * 批量删除字典类型（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:dict:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        dictService.deleteDictTypes(ids);
        return R.ok();
    }

    /**
     * 字典类型下拉选项（全量）
     */
    @GetMapping("/optionselect")
    public R<List<DictType>> optionselect() {
        return R.ok(dictService.selectDictTypeList(null));
    }
}
