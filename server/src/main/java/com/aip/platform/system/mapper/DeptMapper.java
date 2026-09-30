package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.Dept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门数据访问
 */
public interface DeptMapper extends BaseMapper<Dept> {

    /**
     * 查询部门及其全部下级部门ID
     *
     * @param deptId 部门ID
     * @return 部门ID集合（含自身）
     */
    @Select("SELECT dept_id FROM sys_dept WHERE del_flag = 0 "
            + "AND (dept_id = #{deptId} OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', #{deptId}, ',%'))")
    List<Long> selectDeptIdAndChildIds(@Param("deptId") Long deptId);

    /**
     * 查询角色自定义数据权限关联的部门ID
     *
     * @param roleId 角色ID
     * @return 部门ID集合
     */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 统计部门下的子部门数量
     *
     * @param parentId 父部门ID
     * @return 子部门数量
     */
    @Select("SELECT COUNT(1) FROM sys_dept WHERE del_flag = 0 AND parent_id = #{parentId}")
    int countChildByParentId(@Param("parentId") Long parentId);
}
