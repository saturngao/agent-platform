package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.system.entity.Menu;
import com.aip.platform.system.service.ISysMenuService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单权限管理接口
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final ISysMenuService menuService;

    private final ISysRoleService roleService;

    /**
     * 菜单列表（平铺，分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<Menu>> list(Menu menu, PageParam query) {
        List<Menu> menus = menuService.selectMenuList(menu, SecurityUtils.getUserId());
        if (PageParam.isNoPaging(query)) {
            return R.ok(PageResult.page(menus, 1, Math.max(menus.size(), 1)));
        }
        return R.ok(PageResult.page(menus, query.getPageNum(), query.getPageSize()));
    }

    /**
     * 新增菜单
     */
    @PreAuthorize("@permissionChecker.has('system:menu:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody Menu menu) {
        menuService.insertMenu(menu);
        return R.ok();
    }

    /**
     * 修改菜单
     */
    @PreAuthorize("@permissionChecker.has('system:menu:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody Menu menu) {
        menuService.updateMenu(menu);
        return R.ok();
    }

    /**
     * 删除菜单
     */
    @PreAuthorize("@permissionChecker.has('system:menu:remove')")
    @DeleteMapping("/remove/{id}")
    public R<Void> remove(@PathVariable("id") Long id) {
        menuService.deleteMenu(id);
        return R.ok();
    }

    /**
     * 菜单下拉树（当前用户可见菜单树）
     */
    @GetMapping("/tree")
    public R<List<Menu>> tree() {
        return R.ok(menuService.selectMenuTree(SecurityUtils.getUserId()));
    }

    /**
     * 角色菜单授权树：返回全部菜单树与该角色已选中的菜单ID
     */
    @GetMapping("/roleMenuTree/{roleId}")
    public R<Map<String, Object>> roleMenuTree(@PathVariable("roleId") Long roleId) {
        List<Menu> menus = menuService.buildMenuTree(menuService.selectMenuList(null, SecurityUtils.getUserId()));
        Map<String, Object> data = new HashMap<>();
        data.put("menus", menus);
        data.put("checkedKeys", roleService.selectMenuIdsByRoleId(roleId));
        return R.ok(data);
    }
}
