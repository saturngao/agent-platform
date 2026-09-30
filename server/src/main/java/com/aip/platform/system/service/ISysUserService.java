package com.aip.platform.system.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.security.LoginUser;
import com.aip.platform.system.entity.User;

import java.util.List;

/**
 * 系统用户业务接口
 */
public interface ISysUserService {

    /**
     * 分页查询用户（非超级管理员按角色数据范围限制）
     *
     * @param query 分页参数
     * @param user  查询条件
     * @return 分页结果
     */
    PageResult<User> selectUserPage(PageQuery query, User user);

    /**
     * 不分页查询用户列表
     *
     * @param user 查询条件
     * @return 用户列表
     */
    List<User> selectUserList(User user);

    /**
     * 按账号查询用户（含密码）
     *
     * @param username 登录账号
     * @return 用户
     */
    User selectUserByUsername(String username);

    /**
     * 按ID查询用户
     *
     * @param userId 用户ID
     * @return 用户
     */
    User selectUserById(Long userId);

    /**
     * 查询用户已关联的角色ID集合
     *
     * @param userId 用户ID
     * @return 角色ID集合
     */
    List<Long> selectRoleIdsByUserId(Long userId);

    /**
     * 查询用户已关联的岗位ID集合
     *
     * @param userId 用户ID
     * @return 岗位ID集合
     */
    List<Long> selectPostIdsByUserId(Long userId);

    /**
     * 组装登录主体：角色、权限、部门、是否超管
     *
     * @param user 用户
     * @return 登录主体
     */
    LoginUser buildLoginUser(User user);

    /**
     * 新增用户（含角色、岗位关联）
     *
     * @param user 用户
     * @return 影响行数
     */
    int insertUser(User user);

    /**
     * 修改用户（含重建角色、岗位关联）
     *
     * @param user 用户
     * @return 影响行数
     */
    int updateUser(User user);

    /**
     * 批量逻辑删除用户
     *
     * @param userIds 用户ID集合
     * @return 影响行数
     */
    int deleteUsers(List<Long> userIds);

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 明文密码
     * @return 影响行数
     */
    int resetPassword(Long userId, String password);

    /**
     * 用户自助修改密码（校验旧密码）
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码明文
     * @param newPassword 新密码明文
     * @return 影响行数
     */
    int updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 状态 0正常 1停用
     * @return 影响行数
     */
    int updateStatus(Long userId, Integer status);

    /**
     * 记录用户最后登录IP与时间
     *
     * @param userId  用户ID
     * @param loginIp 登录IP
     * @return 影响行数
     */
    int updateLoginInfo(Long userId, String loginIp);

    /**
     * 校验登录账号是否唯一
     *
     * @param user 用户
     * @return true 唯一
     */
    boolean checkUsernameUnique(User user);
}
