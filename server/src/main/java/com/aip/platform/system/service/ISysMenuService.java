package com.aip.platform.system.service;

import com.aip.platform.system.entity.Menu;

import java.util.List;
import java.util.Set;

/**
 * 菜单权限业务接口
 */
public interface ISysMenuService {

    /**
     * 查询菜单列表（管理员返回全部，普通用户按角色过滤）
     *
     * @param menu  查询条件
     * @param userId 当前用户ID
     * @return 菜单列表（平铺）
     */
    List<Menu> selectMenuList(Menu menu, Long userId);

    /**
     * 查询当前用户可见菜单树
     *
     * @param userId 用户ID
     * @return 菜单树
     */
    List<Menu> selectMenuTree(Long userId);

    /**
     * 通用树构建：按 menuId / parentId 嵌套，按 orderNum 排序，返回 parentId=0 的根节点
     *
     * @param menus 平铺菜单列表
     * @return 菜单树
     */
    List<Menu> buildMenuTree(List<Menu> menus);

    /**
     * 查询用户的菜单权限标识
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    Set<String> selectPermsByUserId(Long userId);

    /**
     * 按ID查询菜单
     *
     * @param menuId 菜单ID
     * @return 菜单
     */
    Menu selectMenuById(Long menuId);

    /**
     * 新增菜单
     *
     * @param menu 菜单
     * @return 影响行数
     */
    int insertMenu(Menu menu);

    /**
     * 修改菜单
     *
     * @param menu 菜单
     * @return 影响行数
     */
    int updateMenu(Menu menu);

    /**
     * 删除菜单（存在子菜单时不允许删除）
     *
     * @param menuId 菜单ID
     * @return 影响行数
     */
    int deleteMenu(Long menuId);

    /**
     * 校验同一父级下菜单名称是否唯一
     *
     * @param menu 菜单
     * @return true 唯一
     */
    boolean checkNameUnique(Menu menu);
}
