package com.aip.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.system.entity.Dept;
import com.aip.platform.system.mapper.DeptMapper;
import com.aip.platform.system.mapper.UserMapper;
import com.aip.platform.system.service.ISysDeptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 部门业务实现
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<DeptMapper, Dept> implements ISysDeptService {

    /** 根级父ID */
    private static final Long ROOT_PARENT_ID = 0L;

    /** 祖级路径分隔符 */
    private static final String SEPARATOR = ",";

    private final UserMapper userMapper;

    public SysDeptServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Dept selectDeptById(Long deptId) {
        return deptId == null ? null : baseMapper.selectById(deptId);
    }

    @Override
    public List<Dept> selectDeptList(Dept dept) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<>();
        if (dept != null) {
            if (StrUtil.isNotBlank(dept.getDeptName())) {
                wrapper.like(Dept::getDeptName, dept.getDeptName());
            }
            if (StrUtil.isNotBlank(dept.getLeader())) {
                wrapper.like(Dept::getLeader, dept.getLeader());
            }
            if (dept.getStatus() != null) {
                wrapper.eq(Dept::getStatus, dept.getStatus());
            }
            if (dept.getParentId() != null) {
                wrapper.eq(Dept::getParentId, dept.getParentId());
            }
        }
        wrapper.orderByAsc(Dept::getOrderNum).orderByAsc(Dept::getDeptId);
        return this.list(wrapper);
    }

    @Override
    public List<Dept> buildDeptTree(List<Dept> depts) {
        List<Dept> roots = new ArrayList<>();
        if (depts == null || depts.isEmpty()) {
            return roots;
        }
        List<Dept> sorted = new ArrayList<>(depts);
        // 先按祖级路径再按排序号，保证同级顺序稳定
        sorted.sort(Comparator.comparing(Dept::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Dept::getDeptId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<Long, List<Dept>> groupByParent = new LinkedHashMap<>();
        for (Dept dept : sorted) {
            dept.setChildren(null);
            Long parentId = dept.getParentId() == null ? ROOT_PARENT_ID : dept.getParentId();
            groupByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(dept);
        }
        for (Dept dept : sorted) {
            List<Dept> children = groupByParent.get(dept.getDeptId());
            if (children != null && !children.isEmpty()) {
                dept.setChildren(children);
            }
        }
        return groupByParent.getOrDefault(ROOT_PARENT_ID, new ArrayList<>());
    }

    @Override
    public List<Long> selectDeptIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectDeptIdsByRoleId(roleId);
    }

    @Override
    public List<Long> selectDeptIdAndChildIds(Long deptId) {
        if (deptId == null) {
            return List.of();
        }
        return baseMapper.selectDeptIdAndChildIds(deptId);
    }

    @Override
    public int insertDept(Dept dept) {
        if (dept == null || StrUtil.isBlank(dept.getDeptName())) {
            throw new BusinessException("部门名称不能为空");
        }
        if (!checkDeptNameUnique(dept)) {
            throw new BusinessException("新增部门失败，同一层级下部门名称已存在");
        }
        if (dept.getParentId() == null) {
            dept.setParentId(ROOT_PARENT_ID);
        }
        Dept parent = dept.getParentId().equals(ROOT_PARENT_ID) ? null : baseMapper.selectById(dept.getParentId());
        if (parent != null && Objects.equals(1, parent.getStatus())) {
            throw new BusinessException("上级部门已停用，不允许新增下级部门");
        }
        dept.setAncestors(parent == null
                ? String.valueOf(ROOT_PARENT_ID)
                : parent.getAncestors() + SEPARATOR + parent.getDeptId());
        if (dept.getOrderNum() == null) {
            dept.setOrderNum(0);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(0);
        }
        return baseMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDept(Dept dept) {
        if (dept == null || dept.getDeptId() == null) {
            throw new BusinessException("部门ID不能为空");
        }
        if (!checkDeptNameUnique(dept)) {
            throw new BusinessException("修改部门失败，同一层级下部门名称已存在");
        }
        Dept old = baseMapper.selectById(dept.getDeptId());
        if (old == null) {
            throw new BusinessException("部门不存在");
        }
        if (dept.getParentId() == null) {
            dept.setParentId(old.getParentId());
        }
        Dept parent = dept.getParentId().equals(ROOT_PARENT_ID) ? null : baseMapper.selectById(dept.getParentId());
        dept.setAncestors(parent == null
                ? String.valueOf(ROOT_PARENT_ID)
                : parent.getAncestors() + SEPARATOR + parent.getDeptId());
        int rows = baseMapper.updateById(dept);
        // 父级发生变化时，同步修正下级的祖级路径
        if (!Objects.equals(old.getParentId(), dept.getParentId()) || !Objects.equals(old.getAncestors(), dept.getAncestors())) {
            updateChildrenAncestors(dept.getDeptId(), old.getAncestors(), dept.getAncestors());
        }
        return rows;
    }

    @Override
    public int deleteDept(Long deptId) {
        if (deptId == null) {
            throw new BusinessException("部门ID不能为空");
        }
        if (baseMapper.countChildByParentId(deptId) > 0) {
            throw new BusinessException("存在下级部门，不允许删除");
        }
        if (userMapper.countUserByDeptId(deptId) > 0) {
            throw new BusinessException("部门下已分配用户，不允许删除");
        }
        return baseMapper.deleteById(deptId);
    }

    @Override
    public boolean checkDeptNameUnique(Dept dept) {
        if (dept == null || StrUtil.isBlank(dept.getDeptName())) {
            return true;
        }
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dept::getDeptName, dept.getDeptName())
                .eq(Dept::getParentId, dept.getParentId() == null ? ROOT_PARENT_ID : dept.getParentId());
        if (dept.getDeptId() != null) {
            wrapper.ne(Dept::getDeptId, dept.getDeptId());
        }
        return this.count(wrapper) == 0;
    }

    /** 更新子孙部门的祖级路径前缀 */
    private void updateChildrenAncestors(Long deptId, String oldAncestors, String newAncestors) {
        if (StrUtil.isBlank(oldAncestors)) {
            oldAncestors = String.valueOf(ROOT_PARENT_ID);
        }
        String oldPrefix = oldAncestors + SEPARATOR + deptId;
        for (Dept child : this.list()) {
            String ancestors = child.getAncestors();
            if (StrUtil.isBlank(ancestors) || child.getDeptId().equals(deptId)) {
                continue;
            }
            if (ancestors.equals(oldPrefix) || ancestors.startsWith(oldPrefix + SEPARATOR)) {
                Dept update = new Dept();
                update.setDeptId(child.getDeptId());
                update.setAncestors(newAncestors + ancestors.substring(oldAncestors.length()));
                baseMapper.updateById(update);
            }
        }
    }
}
