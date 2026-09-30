package com.aip.platform.system.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.Menu;
import com.aip.platform.system.entity.Role;

import java.util.List;
import java.util.Set;

/**
 * 角色业务接口
 */
public interface ISysRoleService {

    /**
     * 分页查询角色
     *
     * @param query 分页参数
     * @param role  查询条件
     * @return 分页结果
     */
    PageResult<Role> selectRolePage(PageQuery query, Role role);

    /**
     * 不分页查询角色列表
     *
     * @param role 查询条件
     * @return 角色列表
     */
    List<Role> selectRoleList(Role role);

    /**
     * 查询用户拥有的角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> selectRolesByUserId(Long userId);

    /**
     * 查询用户拥有的角色标识
     *
     * @param userId 用户ID
     * @return 角色标识集合
     */
    List<String> selectRoleKeysByUserId(Long userId);

    /**
     * 查询用户的菜单权限标识
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 查询用户可见的菜单树
     *
     * @param userId 用户ID
     * @return 菜单树
     */
    List<Menu> selectMenuTreeByUserId(Long userId);

    /**
     * 查询角色已分配的菜单ID
     *
     * @param roleId 角色ID
     * @return 菜单ID集合
     */
    List<Long> selectMenuIdsByRoleId(Long roleId);

    /**
     * 新增角色（含角色菜单授权）
     *
     * @param role 角色
     * @return 影响行数
     */
    int insertRole(Role role);

    /**
     * 修改角色（含角色菜单授权）
     *
     * @param role 角色
     * @return 影响行数
     */
    int updateRole(Role role);

    /**
     * 批量删除角色（已分配用户的角色不允许删除）
     *
     * @param roleIds 角色ID集合
     * @return 影响行数
     */
    int deleteRoles(List<Long> roleIds);

    /**
     * 修改角色状态
     *
     * @param roleId 角色ID
     * @param status 状态 0正常 1停用
     * @return 影响行数
     */
    int changeStatus(Long roleId, Integer status);

    /**
     * 授权角色数据范围（更新 data_scope 并重建 sys_role_dept）
     *
     * @param role 角色
     * @return 影响行数
     */
    int authDataScope(Role role);

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色
     * @return true 唯一
     */
    boolean checkRoleNameUnique(Role role);

    /**
     * 校验角色权限字符是否唯一
     *
     * @param role 角色
     * @return true 唯一
     */
    boolean checkRoleKeyUnique(Role role);
}
