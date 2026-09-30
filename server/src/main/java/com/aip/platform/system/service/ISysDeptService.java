package com.aip.platform.system.service;

import com.aip.platform.system.entity.Dept;

import java.util.List;

/**
 * 部门业务接口
 */
public interface ISysDeptService {

    /**
     * 按ID查询部门
     *
     * @param deptId 部门ID
     * @return 部门
     */
    Dept selectDeptById(Long deptId);

    /**
     * 查询部门列表（平铺，按 orderNum 排序）
     *
     * @param dept 查询条件
     * @return 部门列表
     */
    List<Dept> selectDeptList(Dept dept);

    /**
     * 构建部门树（按 deptId / parentId 嵌套，orderNum 排序）
     *
     * @param depts 平铺部门列表
     * @return 部门树
     */
    List<Dept> buildDeptTree(List<Dept> depts);

    /**
     * 查询角色自定义数据范围关联的部门ID
     *
     * @param roleId 角色ID
     * @return 部门ID集合
     */
    List<Long> selectDeptIdsByRoleId(Long roleId);

    /**
     * 查询部门及其全部下级部门ID
     *
     * @param deptId 部门ID
     * @return 部门ID集合（含自身）
     */
    List<Long> selectDeptIdAndChildIds(Long deptId);

    /**
     * 新增部门
     *
     * @param dept 部门
     * @return 影响行数
     */
    int insertDept(Dept dept);

    /**
     * 修改部门（父级变化时同步修正下级祖级路径）
     *
     * @param dept 部门
     * @return 影响行数
     */
    int updateDept(Dept dept);

    /**
     * 删除部门（存在下级或已分配用户时不允许删除）
     *
     * @param deptId 部门ID
     * @return 影响行数
     */
    int deleteDept(Long deptId);

    /**
     * 校验同一父级下部门名称是否唯一
     *
     * @param dept 部门
     * @return true 唯一
     */
    boolean checkDeptNameUnique(Dept dept);
}
