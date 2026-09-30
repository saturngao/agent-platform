package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色数据访问：角色查询 + 角色菜单 / 角色部门关联维护
 */
public interface RoleMapper extends BaseMapper<Role> {

    /** 角色查询列 */
    String ROLE_COLUMNS = "SELECT r.role_id, r.role_name, r.role_key, r.role_sort, r.data_scope, "
            + "r.status, r.del_flag, r.remark, r.create_by, r.create_time, r.update_by, r.update_time "
            + "FROM sys_role r ";

    /**
     * 查询用户拥有的角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Select("<script>" + ROLE_COLUMNS
            + "LEFT JOIN sys_user_role ur ON ur.role_id = r.role_id "
            + "WHERE ur.user_id = #{userId} AND r.del_flag = 0 AND r.status = 0 "
            + "ORDER BY r.role_sort"
            + "</script>")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的角色ID集合
     *
     * @param userId 用户ID
     * @return 角色ID集合
     */
    @Select("SELECT ur.role_id FROM sys_user_role ur "
            + "LEFT JOIN sys_role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.del_flag = 0 AND r.status = 0")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的角色标识集合
     *
     * @param userId 用户ID
     * @return 角色标识集合
     */
    @Select("SELECT r.role_key FROM sys_user_role ur "
            + "LEFT JOIN sys_role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.del_flag = 0 AND r.status = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的菜单权限标识集合
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m "
            + "LEFT JOIN sys_role_menu rm ON rm.menu_id = m.menu_id "
            + "LEFT JOIN sys_user_role ur ON ur.role_id = rm.role_id "
            + "LEFT JOIN sys_role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND m.status = 0 AND r.status = 0 AND r.del_flag = 0 "
            + "AND m.perms IS NOT NULL AND m.perms != ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查询角色已分配的菜单ID
     *
     * @param roleId 角色ID
     * @return 菜单ID集合
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色自定义数据权限的部门ID
     *
     * @param roleId 角色ID
     * @return 部门ID集合
     */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量新增角色菜单关联
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID集合
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO sys_role_menu(role_id, menu_id) VALUES "
            + "<foreach collection=\"menuIds\" item=\"menuId\" separator=\",\">(#{roleId}, #{menuId})</foreach>"
            + "</script>")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    /**
     * 删除角色的全部菜单关联
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenuByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量新增角色部门关联（自定义数据权限）
     *
     * @param roleId  角色ID
     * @param deptIds 部门ID集合
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO sys_role_dept(role_id, dept_id) VALUES "
            + "<foreach collection=\"deptIds\" item=\"deptId\" separator=\",\">(#{roleId}, #{deptId})</foreach>"
            + "</script>")
    int insertRoleDept(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);

    /**
     * 删除角色的全部部门关联
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteRoleDeptByRoleId(@Param("roleId") Long roleId);
}
