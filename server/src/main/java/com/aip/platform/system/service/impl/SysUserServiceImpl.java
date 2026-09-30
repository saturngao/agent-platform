package com.aip.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.Constants;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.security.LoginUser;
import com.aip.platform.system.entity.Dept;
import com.aip.platform.system.entity.Role;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.mapper.DeptMapper;
import com.aip.platform.system.mapper.UserMapper;
import com.aip.platform.system.service.ISysRoleService;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统用户业务实现
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<UserMapper, User> implements ISysUserService {

    private final ISysRoleService roleService;

    private final DeptMapper deptMapper;

    public SysUserServiceImpl(ISysRoleService roleService, DeptMapper deptMapper) {
        this.roleService = roleService;
        this.deptMapper = deptMapper;
    }

    @Override
    public PageResult<User> selectUserPage(PageQuery query, User user) {
        User condition = user == null ? new User() : user;
        List<Long> scopeDeptIds = buildDataScope(condition);
        if (PageParam.isNoPaging(query)) {
            List<User> all = baseMapper.selectUserList(condition, scopeDeptIds);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<User> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<User> result = baseMapper.selectUserPage(page, condition, scopeDeptIds);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<User> selectUserList(User user) {
        User condition = user == null ? new User() : user;
        List<Long> scopeDeptIds = buildDataScope(condition);
        return baseMapper.selectUserList(condition, scopeDeptIds);
    }

    @Override
    public User selectUserByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return baseMapper.selectUserByUsername(username);
    }

    @Override
    public User selectUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        return baseMapper.selectById(userId);
    }

    @Override
    public List<Long> selectRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    public List<Long> selectPostIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectPostIdsByUserId(userId);
    }

    @Override
    public LoginUser buildLoginUser(User user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPassword());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setDeptName(resolveDeptName(user));
        loginUser.setLoginTime(LocalDateTime.now());

        List<String> roleKeys = roleService.selectRoleKeysByUserId(user.getUserId());
        loginUser.setRoleKeys(roleKeys == null ? new ArrayList<>() : new ArrayList<>(roleKeys));
        Set<String> permissions = roleService.selectMenuPermsByUserId(user.getUserId());
        loginUser.setPermissions(permissions == null ? new LinkedHashSet<>() : permissions);

        boolean superAdmin = Constants.SUPER_ADMIN.equals(user.getUsername())
                || (roleKeys != null && roleKeys.contains(Constants.SUPER_ADMIN));
        loginUser.setSuperAdmin(superAdmin);
        return loginUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(User user) {
        if (user == null || StrUtil.isBlank(user.getUsername())) {
            throw new BusinessException("登录账号不能为空");
        }
        if (!checkUsernameUnique(user)) {
            throw new BusinessException("新增用户失败，登录账号已存在");
        }
        if (StrUtil.isBlank(user.getPassword())) {
            user.setPassword(SecurityUtils.encryptPassword(Constants.SUPER_ADMIN.equals(user.getUsername())
                    ? "admin123" : "123456"));
        } else {
            user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        }
        if (user.getStatus() == null) {
            user.setStatus(Constants.STATUS_NORMAL);
        }
        if (StrUtil.isBlank(user.getUserType())) {
            user.setUserType("sys_user");
        }
        int rows = baseMapper.insert(user);
        if (CollUtil.isNotEmpty(user.getRoleIds())) {
            baseMapper.insertUserRole(user.getUserId(), user.getRoleIds());
        }
        if (CollUtil.isNotEmpty(user.getPostIds())) {
            baseMapper.insertUserPost(user.getUserId(), user.getPostIds());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (StrUtil.isNotBlank(user.getUsername()) && !checkUsernameUnique(user)) {
            throw new BusinessException("修改用户失败，登录账号已存在");
        }
        // 密码字段不参与常规更新
        user.setPassword(null);
        int rows = baseMapper.updateById(user);
        baseMapper.deleteUserRoleByUserId(user.getUserId());
        if (CollUtil.isNotEmpty(user.getRoleIds())) {
            baseMapper.insertUserRole(user.getUserId(), user.getRoleIds());
        }
        baseMapper.deleteUserPostByUserId(user.getUserId());
        if (CollUtil.isNotEmpty(user.getPostIds())) {
            baseMapper.insertUserPost(user.getUserId(), user.getPostIds());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUsers(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            throw new BusinessException("请选择要删除的用户");
        }
        Long currentUserId = SecurityUtils.getUserId();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            if (userId.equals(currentUserId)) {
                throw new BusinessException("不允许删除当前登录用户");
            }
        }
        baseMapper.deleteUserRoleByUserIds(userIds);
        baseMapper.deleteUserPostByUserIds(userIds);
        return baseMapper.deleteBatchIds(userIds);
    }

    @Override
    public int resetPassword(Long userId, String password) {
        if (userId == null || StrUtil.isBlank(password)) {
            throw new BusinessException("用户ID或新密码不能为空");
        }
        User user = new User();
        user.setUserId(userId);
        user.setPassword(SecurityUtils.encryptPassword(password));
        return baseMapper.updateById(user);
    }

    @Override
    public int updatePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || StrUtil.isBlank(oldPassword) || StrUtil.isBlank(newPassword)) {
            throw new BusinessException("旧密码与新密码不能为空");
        }
        User stored = baseMapper.selectById(userId);
        if (stored == null) {
            throw new BusinessException("用户不存在");
        }
        if (!SecurityUtils.matchesPassword(oldPassword, stored.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        if (SecurityUtils.matchesPassword(newPassword, stored.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }
        return resetPassword(userId, newPassword);
    }

    @Override
    public int updateStatus(Long userId, Integer status) {
        if (userId == null || status == null) {
            throw new BusinessException("用户ID或状态不能为空");
        }
        User user = new User();
        user.setUserId(userId);
        user.setStatus(status);
        return baseMapper.updateById(user);
    }

    @Override
    public int updateLoginInfo(Long userId, String loginIp) {
        if (userId == null) {
            return 0;
        }
        User user = new User();
        user.setUserId(userId);
        user.setLoginIp(loginIp);
        user.setLoginDate(LocalDateTime.now());
        return baseMapper.updateById(user);
    }

    @Override
    public boolean checkUsernameUnique(User user) {
        if (user == null || StrUtil.isBlank(user.getUsername())) {
            return true;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (user.getUserId() != null) {
            wrapper.ne(User::getUserId, user.getUserId());
        }
        return this.count(wrapper) == 0;
    }

    /** 查询部门名称，避免调用方未携带时的空值 */
    private String resolveDeptName(User user) {
        if (StrUtil.isNotBlank(user.getDeptName())) {
            return user.getDeptName();
        }
        if (user.getDeptId() == null) {
            return null;
        }
        Dept dept = deptMapper.selectById(user.getDeptId());
        return dept == null ? null : dept.getDeptName();
    }

    /**
     * 依据当前用户角色的数据范围构造查询约束
     *
     * <p>返回值为自定义数据范围限定的部门ID集合，同时会按需回填
     * {@code scopeDeptId}（部门及以下）与 {@code userId}（仅本人）。</p>
     */
    private List<Long> buildDataScope(User condition) {
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null || SecurityUtils.isSuperAdmin()) {
            return null;
        }
        List<Role> roles = roleService.selectRolesByUserId(currentUserId);
        if (CollUtil.isEmpty(roles)) {
            condition.setUserId(currentUserId);
            return null;
        }
        boolean all = false;
        boolean selfOnly = false;
        Long scopeDeptId = null;
        Set<Long> customDeptIds = new LinkedHashSet<>();
        Long deptId = SecurityUtils.getDeptId();
        for (Role role : roles) {
            String dataScope = role.getDataScope();
            if (Constants.DataScope.ALL.equals(dataScope)) {
                all = true;
                break;
            }
            if (Constants.DataScope.CUSTOM.equals(dataScope)) {
                List<Long> ids = deptMapper.selectDeptIdsByRoleId(role.getRoleId());
                if (CollUtil.isNotEmpty(ids)) {
                    customDeptIds.addAll(ids);
                }
            } else if (Constants.DataScope.DEPT.equals(dataScope)
                    || Constants.DataScope.DEPT_AND_CHILD.equals(dataScope)) {
                scopeDeptId = deptId;
            } else if (Constants.DataScope.SELF.equals(dataScope)) {
                selfOnly = true;
            }
        }
        if (all) {
            return null;
        }
        if (scopeDeptId != null) {
            condition.setScopeDeptId(scopeDeptId);
            return null;
        }
        if (!customDeptIds.isEmpty()) {
            return new ArrayList<>(customDeptIds);
        }
        if (selfOnly) {
            condition.setUserId(currentUserId);
        }
        return null;
    }
}
