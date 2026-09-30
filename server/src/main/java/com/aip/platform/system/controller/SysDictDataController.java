package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.DictData;
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
 * 字典数据管理接口
 */
@RestController
@RequestMapping("/api/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {

    private final ISysDictService dictService;

    /**
     * 字典数据列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<DictData>> list(DictData dictData, PageParam query) {
        return R.ok(dictService.selectDictDataPage(query, dictData));
    }

    /**
     * 字典数据详情（修改弹窗回显）
     */
    @GetMapping("/{dictCode}")
    public R<DictData> detail(@PathVariable("dictCode") Long dictCode) {
        DictData dictData = dictService.selectDictDataById(dictCode);
        if (dictData == null) {
            return R.fail("字典数据不存在");
        }
        return R.ok(dictData);
    }

    /**
     * 新增字典数据
     */
    @PreAuthorize("@permissionChecker.has('system:dict:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody DictData dictData) {
        dictService.insertDictData(dictData);
        return R.ok();
    }

    /**
     * 修改字典数据
     */
    @PreAuthorize("@permissionChecker.has('system:dict:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody DictData dictData) {
        dictService.updateDictData(dictData);
        return R.ok();
    }

    /**
     * 批量删除字典数据（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:dict:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        dictService.deleteDictDatas(ids);
        return R.ok();
    }

    /**
     * 按字典类型查询字典数据
     */
    @GetMapping("/type/{dictType}")
    public R<List<DictData>> dictType(@PathVariable("dictType") String dictType) {
        return R.ok(dictService.selectDictDataByType(dictType));
    }
}
