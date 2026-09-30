package com.aip.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.system.entity.Menu;
import com.aip.platform.system.entity.Role;
import com.aip.platform.system.mapper.DeptMapper;
import com.aip.platform.system.mapper.MenuMapper;
import com.aip.platform.system.mapper.RoleMapper;
import com.aip.platform.system.mapper.UserMapper;
import com.aip.platform.system.service.ISysMenuService;
import com.aip.platform.system.service.ISysRoleService;
import com.aip.platform.system.vo.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 角色业务实现
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements ISysRoleService {

    private final MenuMapper menuMapper;

    private final UserMapper userMapper;

    private final DeptMapper deptMapper;

    private final ISysMenuService menuService;

    public SysRoleServiceImpl(MenuMapper menuMapper, UserMapper userMapper, DeptMapper deptMapper, ISysMenuService menuService) {
        this.menuMapper = menuMapper;
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.menuService = menuService;
    }

    @Override
    public PageResult<Role> selectRolePage(PageQuery query, Role role) {
        if (PageParam.isNoPaging(query)) {
            List<Role> all = selectRoleList(role);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<Role> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Role> result = baseMapper.selectPage(page, buildQueryWrapper(role));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<Role> selectRoleList(Role role) {
        return this.list(buildQueryWrapper(role));
    }

    @Override
    public List<Role> selectRolesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectRolesByUserId(userId);
    }

    @Override
    public List<String> selectRoleKeysByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectRoleKeysByUserId(userId);
    }

    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        if (SecurityUtils.isSuperAdmin()) {
            return menuService.selectPermsByUserId(userId);
        }
        List<String> perms = baseMapper.selectPermsByUserId(userId);
        if (perms == null) {
            return Set.of();
        }
        return Set.copyOf(perms.stream().filter(StrUtil::isNotBlank).toList());
    }

    @Override
    public List<Menu> selectMenuTreeByUserId(Long userId) {
        List<Menu> menus;
        if (SecurityUtils.isSuperAdmin()) {
            menus = menuMapper.selectMenuTreeAll();
        } else {
            menus = menuMapper.selectMenuListByUserId(userId);
        }
        return menuService.buildMenuTree(menus);
    }

    @Override
    public List<Long> selectMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    public List<Long> selectDeptIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return deptMapper.selectDeptIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertRole(Role role) {
        validateRole(role);
        int rows = baseMapper.insert(role);
        insertRoleMenu(role);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRole(Role role) {
        if (role == null || role.getRoleId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        validateRole(role);
        int rows = baseMapper.updateById(role);
        baseMapper.deleteRoleMenuByRoleId(role.getRoleId());
        insertRoleMenu(role);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoles(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            throw new BusinessException("请选择要删除的角色");
        }
        int rows = 0;
        for (Long roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            if (userMapper.countUserByRoleId(roleId) > 0) {
                throw new BusinessException("角色已分配给用户，不允许删除");
            }
            baseMapper.deleteRoleMenuByRoleId(roleId);
            baseMapper.deleteRoleDeptByRoleId(roleId);
            rows += baseMapper.deleteById(roleId);
        }
        return rows;
    }

    @Override
    public int changeStatus(Long roleId, Integer status) {
        if (roleId == null || status == null) {
            throw new BusinessException("角色ID或状态不能为空");
        }
        Role role = new Role();
        role.setRoleId(roleId);
        role.setStatus(status);
        return baseMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int authDataScope(Role role) {
        if (role == null || role.getRoleId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        Role update = new Role();
        update.setRoleId(role.getRoleId());
        update.setDataScope(role.getDataScope());
        int rows = baseMapper.updateById(update);
        baseMapper.deleteRoleDeptByRoleId(role.getRoleId());
        if (CollUtil.isNotEmpty(role.getDeptIds())) {
            baseMapper.insertRoleDept(role.getRoleId(), role.getDeptIds());
        }
        return rows;
    }

    @Override
    public boolean checkRoleNameUnique(Role role) {
        if (role == null || StrUtil.isBlank(role.getRoleName())) {
            return true;
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleName, role.getRoleName());
        if (role.getRoleId() != null) {
            wrapper.ne(Role::getRoleId, role.getRoleId());
        }
        return this.count(wrapper) == 0;
    }

    @Override
    public boolean checkRoleKeyUnique(Role role) {
        if (role == null || StrUtil.isBlank(role.getRoleKey())) {
            return true;
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleKey, role.getRoleKey());
        if (role.getRoleId() != null) {
            wrapper.ne(Role::getRoleId, role.getRoleId());
        }
        return this.count(wrapper) == 0;
    }

    /** 新增/修改时的业务校验 */
    private void validateRole(Role role) {
        if (role == null) {
            throw new BusinessException("角色信息不能为空");
        }
        if (!checkRoleNameUnique(role)) {
            throw new BusinessException("角色名称已存在");
        }
        if (!checkRoleKeyUnique(role)) {
            throw new BusinessException("角色权限字符已存在");
        }
    }

    /** 写入角色菜单关联 */
    private void insertRoleMenu(Role role) {
        if (CollUtil.isEmpty(role.getMenuIds())) {
            return;
        }
        baseMapper.insertRoleMenu(role.getRoleId(), role.getMenuIds());
    }

    /** 构造角色查询条件 */
    private LambdaQueryWrapper<Role> buildQueryWrapper(Role role) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (role != null) {
            if (StrUtil.isNotBlank(role.getRoleName())) {
                wrapper.like(Role::getRoleName, role.getRoleName());
            }
            if (StrUtil.isNotBlank(role.getRoleKey())) {
                wrapper.like(Role::getRoleKey, role.getRoleKey());
            }
            if (StrUtil.isNotBlank(role.getDataScope())) {
                wrapper.eq(Role::getDataScope, role.getDataScope());
            }
            if (role.getStatus() != null) {
                wrapper.eq(Role::getStatus, role.getStatus());
            }
        }
        wrapper.orderByAsc(Role::getRoleSort);
        return wrapper;
    }
}
