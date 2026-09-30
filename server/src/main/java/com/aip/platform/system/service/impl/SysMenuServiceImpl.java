package com.aip.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.system.entity.Menu;
import com.aip.platform.system.mapper.MenuMapper;
import com.aip.platform.system.service.ISysMenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单权限业务实现
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements ISysMenuService {

    /** 根级父ID */
    private static final Long ROOT_PARENT_ID = 0L;

    @Override
    public List<Menu> selectMenuList(Menu menu, Long userId) {
        List<Menu> menus;
        if (SecurityUtils.isSuperAdmin()) {
            menus = this.list(buildQueryWrapper(menu));
        } else {
            menus = baseMapper.selectMenuListByUserId(userId);
            menus = filterByCondition(menus, menu);
        }
        menus.sort(Comparator.comparing(Menu::getParentId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Menu::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())));
        return menus;
    }

    @Override
    public List<Menu> selectMenuTree(Long userId) {
        List<Menu> menus;
        if (SecurityUtils.isSuperAdmin()) {
            menus = baseMapper.selectMenuTreeAll();
        } else {
            menus = baseMapper.selectMenuListByUserId(userId);
        }
        return buildMenuTree(menus);
    }

    @Override
    public List<Menu> buildMenuTree(List<Menu> menus) {
        List<Menu> roots = new ArrayList<>();
        if (menus == null || menus.isEmpty()) {
            return roots;
        }
        List<Menu> sorted = new ArrayList<>(menus);
        sorted.sort(Comparator.comparing(Menu::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Menu::getMenuId, Comparator.nullsLast(Comparator.naturalOrder())));

        // 一次性按父ID分组，构建过程不再查库
        Map<Long, List<Menu>> groupByParent = new LinkedHashMap<>();
        for (Menu menu : sorted) {
            menu.setChildren(null);
            Long parentId = menu.getParentId() == null ? ROOT_PARENT_ID : menu.getParentId();
            groupByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(menu);
        }
        for (Menu menu : sorted) {
            List<Menu> children = groupByParent.get(menu.getMenuId());
            if (children != null && !children.isEmpty()) {
                menu.setChildren(children);
            }
        }
        return groupByParent.getOrDefault(ROOT_PARENT_ID, new ArrayList<>());
    }

    @Override
    public Set<String> selectPermsByUserId(Long userId) {
        List<String> perms;
        if (SecurityUtils.isSuperAdmin()) {
            LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(Menu::getPerms)
                    .ne(Menu::getPerms, "")
                    .eq(Menu::getStatus, 0);
            perms = this.list(wrapper).stream()
                    .map(Menu::getPerms)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        } else {
            perms = baseMapper.selectPermsByUserId(userId);
        }
        return perms.stream().filter(StrUtil::isNotBlank).collect(Collectors.toSet());
    }

    @Override
    public Menu selectMenuById(Long menuId) {
        if (menuId == null) {
            return null;
        }
        return this.getById(menuId);
    }

    @Override
    public int insertMenu(Menu menu) {
        if (!checkNameUnique(menu)) {
            throw new BusinessException("新增菜单失败，同一层级下菜单名称已存在");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(ROOT_PARENT_ID);
        }
        if (menu.getOrderNum() == null) {
            menu.setOrderNum(0);
        }
        return baseMapper.insert(menu);
    }

    @Override
    public int updateMenu(Menu menu) {
        if (!checkNameUnique(menu)) {
            throw new BusinessException("修改菜单失败，同一层级下菜单名称已存在");
        }
        return baseMapper.updateById(menu);
    }

    @Override
    public int deleteMenu(Long menuId) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        if (baseMapper.countChildByParentId(menuId) > 0) {
            throw new BusinessException("存在子菜单，不允许删除");
        }
        return baseMapper.deleteById(menuId);
    }

    @Override
    public boolean checkNameUnique(Menu menu) {
        if (menu == null || StrUtil.isBlank(menu.getMenuName())) {
            return true;
        }
        Long parentId = menu.getParentId() == null ? ROOT_PARENT_ID : menu.getParentId();
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getMenuName, menu.getMenuName())
                .eq(Menu::getParentId, parentId);
        if (menu.getMenuId() != null) {
            wrapper.ne(Menu::getMenuId, menu.getMenuId());
        }
        return this.count(wrapper) == 0;
    }

    /** 构造管理员可见菜单的查询条件 */
    private LambdaQueryWrapper<Menu> buildQueryWrapper(Menu menu) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        if (menu != null) {
            if (StrUtil.isNotBlank(menu.getMenuName())) {
                wrapper.like(Menu::getMenuName, menu.getMenuName());
            }
            if (StrUtil.isNotBlank(menu.getMenuType())) {
                wrapper.eq(Menu::getMenuType, menu.getMenuType());
            }
            if (StrUtil.isNotBlank(menu.getPerms())) {
                wrapper.like(Menu::getPerms, menu.getPerms());
            }
            if (menu.getStatus() != null) {
                wrapper.eq(Menu::getStatus, menu.getStatus());
            }
            if (menu.getVisible() != null) {
                wrapper.eq(Menu::getVisible, menu.getVisible());
            }
        }
        wrapper.orderByAsc(Menu::getParentId).orderByAsc(Menu::getOrderNum);
        return wrapper;
    }

    /** 对已按角色过滤的菜单结果再做条件过滤 */
    private List<Menu> filterByCondition(List<Menu> menus, Menu menu) {
        if (menus == null) {
            return new ArrayList<>();
        }
        if (menu == null) {
            return menus;
        }
        return menus.stream()
                .filter(item -> StrUtil.isBlank(menu.getMenuName())
                        || (item.getMenuName() != null && item.getMenuName().contains(menu.getMenuName())))
                .filter(item -> StrUtil.isBlank(menu.getMenuType())
                        || menu.getMenuType().equals(item.getMenuType()))
                .filter(item -> menu.getStatus() == null || Objects.equals(menu.getStatus(), item.getStatus()))
                .collect(Collectors.toList());
    }
}
