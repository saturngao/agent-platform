package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.Dept;
import com.aip.platform.system.service.ISysDeptService;
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
 * 部门管理接口
 */
@RestController
@RequestMapping("/api/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final ISysDeptService deptService;

    /**
     * 部门列表（平铺，分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<Dept>> list(Dept dept, PageParam query) {
        List<Dept> depts = deptService.selectDeptList(dept);
        if (PageParam.isNoPaging(query)) {
            return R.ok(PageResult.page(depts, 1, Math.max(depts.size(), 1)));
        }
        return R.ok(PageResult.page(depts, query.getPageNum(), query.getPageSize()));
    }

    /**
     * 部门详情（修改弹窗回显）
     */
    @GetMapping("/{deptId}")
    public R<Dept> detail(@PathVariable("deptId") Long deptId) {
        Dept dept = deptService.selectDeptById(deptId);
        if (dept == null) {
            return R.fail("部门不存在");
        }
        return R.ok(dept);
    }

    /**
     * 新增部门
     */
    @PreAuthorize("@permissionChecker.has('system:dept:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody Dept dept) {
        deptService.insertDept(dept);
        return R.ok();
    }

    /**
     * 修改部门
     */
    @PreAuthorize("@permissionChecker.has('system:dept:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody Dept dept) {
        deptService.updateDept(dept);
        return R.ok();
    }

    /**
     * 删除部门
     */
    @PreAuthorize("@permissionChecker.has('system:dept:remove')")
    @DeleteMapping("/remove/{id}")
    public R<Void> remove(@PathVariable("id") Long id) {
        deptService.deleteDept(id);
        return R.ok();
    }

    /**
     * 部门下拉树
     */
    @GetMapping("/tree")
    public R<List<Dept>> tree(Dept dept) {
        return R.ok(deptService.buildDeptTree(deptService.selectDeptList(dept)));
    }
}
