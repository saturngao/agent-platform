package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.Role;
import com.aip.platform.system.service.ISysRoleService;
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
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final ISysRoleService roleService;

    /**
     * 角色列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<Role>> list(Role role, PageParam query) {
        return R.ok(roleService.selectRolePage(query, role));
    }

    /**
     * 新增角色
     */
    @PreAuthorize("@permissionChecker.has('system:role:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody Role role) {
        roleService.insertRole(role);
        return R.ok();
    }

    /**
     * 修改角色（含菜单授权）
     */
    @PreAuthorize("@permissionChecker.has('system:role:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody Role role) {
        roleService.updateRole(role);
        return R.ok();
    }

    /**
     * 批量删除角色（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:role:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        roleService.deleteRoles(ids);
        return R.ok();
    }

    /**
     * 修改角色状态
     */
    @PreAuthorize("@permissionChecker.has('system:role:edit')")
    @PutMapping("/status")
    public R<Void> status(@RequestBody Role role) {
        roleService.changeStatus(role.getRoleId(), role.getStatus());
        return R.ok();
    }

    /**
     * 授权角色数据范围
     */
    @PreAuthorize("@permissionChecker.has('system:role:edit')")
    @PutMapping("/dataScope")
    public R<Void> dataScope(@RequestBody Role role) {
        roleService.authDataScope(role);
        return R.ok();
    }

    /**
     * 查询角色已选中的菜单ID数组
     */
    @GetMapping("/menuTree/{roleId}")
    public R<List<Long>> menuTree(@PathVariable("roleId") Long roleId) {
        return R.ok(roleService.selectMenuIdsByRoleId(roleId));
    }

    /**
     * 查询角色已选中的部门ID数组（数据范围为"自定数据"时回显）
     */
    @GetMapping("/deptTree/{roleId}")
    public R<List<Long>> deptTree(@PathVariable("roleId") Long roleId) {
        return R.ok(roleService.selectDeptIdsByRoleId(roleId));
    }
}
