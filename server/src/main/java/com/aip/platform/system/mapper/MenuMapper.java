package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.Menu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单权限数据访问
 */
public interface MenuMapper extends BaseMapper<Menu> {

    /** 菜单查询列 */
    String MENU_COLUMNS = "SELECT m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component, "
            + "m.query, m.is_frame, m.is_cache, m.menu_type, m.visible, m.status, m.perms, m.icon, "
            + "m.remark, m.create_by, m.create_time, m.update_time FROM sys_menu m ";

    /**
     * 查询全部可用菜单（目录与菜单），用于构建菜单树
     *
     * @return 菜单列表
     */
    @Select(MENU_COLUMNS + "WHERE m.menu_type IN ('M', 'C') AND m.status = 0 "
            + "ORDER BY m.parent_id, m.order_num")
    List<Menu> selectMenuTreeAll();

    /**
     * 查询用户可见菜单（管理员返回全部，否则按角色菜单过滤）
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Select("<script>" + MENU_COLUMNS
            + "LEFT JOIN sys_role_menu rm ON rm.menu_id = m.menu_id "
            + "LEFT JOIN sys_user_role ur ON ur.role_id = rm.role_id "
            + "LEFT JOIN sys_role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND m.menu_type IN ('M', 'C') AND m.status = 0 "
            + "AND r.status = 0 AND r.del_flag = 0 "
            + "ORDER BY m.parent_id, m.order_num"
            + "</script>")
    List<Menu> selectMenuListByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的菜单权限标识
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
     * 统计父菜单下的子节点数量
     *
     * @param parentId 父菜单ID
     * @return 子节点数量
     */
    @Select("SELECT COUNT(1) FROM sys_menu WHERE parent_id = #{parentId}")
    int countChildByParentId(@Param("parentId") Long parentId);
}
