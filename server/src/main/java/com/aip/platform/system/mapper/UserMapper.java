package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户数据访问
 *
 * <p>复杂查询使用 @Select + &lt;script&gt; 动态 SQL，不依赖 XML。</p>
 */
public interface UserMapper extends BaseMapper<User> {

    /** 查询列：不含密码，避免敏感信息外泄 */
    String USER_COLUMNS = "SELECT u.user_id, u.dept_id, u.username, u.nickname, u.user_type, u.email, "
            + "u.phonenumber, u.sex, u.avatar, u.status, u.del_flag, u.login_ip, u.login_date, "
            + "u.remark, u.create_by, u.create_time, u.update_by, u.update_time, d.dept_name "
            + "FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
            + "WHERE u.del_flag = 0 ";

    /** 动态条件 */
    String USER_CONDITIONS = "<if test=\"q.username != null and q.username != ''\">"
            + " AND u.username LIKE concat('%', #{q.username}, '%')</if>"
            + "<if test=\"q.nickname != null and q.nickname != ''\">"
            + " AND u.nickname LIKE concat('%', #{q.nickname}, '%')</if>"
            + "<if test=\"q.phonenumber != null and q.phonenumber != ''\">"
            + " AND u.phonenumber LIKE concat('%', #{q.phonenumber}, '%')</if>"
            + "<if test=\"q.email != null and q.email != ''\">"
            + " AND u.email LIKE concat('%', #{q.email}, '%')</if>"
            + "<if test=\"q.status != null\"> AND u.status = #{q.status}</if>"
            + "<if test=\"q.userId != null\"> AND u.user_id = #{q.userId}</if>"
            // 指定部门：本部门及其全部下级
            + "<if test=\"q.deptId != null\">"
            + " AND (u.dept_id = #{q.deptId}"
            + " OR d.ancestors LIKE concat('%', #{q.deptId}, '%'))</if>"
            // 数据权限：部门及以下
            + "<if test=\"q.scopeDeptId != null\">"
            + " AND (u.dept_id = #{q.scopeDeptId}"
            + " OR d.ancestors LIKE concat('%', #{q.scopeDeptId}, '%'))</if>"
            // 数据权限：自定义部门集合
            + "<if test=\"deptIds != null and deptIds.size() &gt; 0\">"
            + " AND u.dept_id IN <foreach collection=\"deptIds\" item=\"did\" open=\"(\" separator=\",\" close=\")\">"
            + "#{did}</foreach></if>"
            // 创建时间范围
            + "<if test=\"q.beginTime != null and q.beginTime != ''\">"
            + " AND u.create_time &gt;= #{q.beginTime}</if>"
            + "<if test=\"q.endTime != null and q.endTime != ''\">"
            + " AND u.create_time &lt;= concat(#{q.endTime}, ' 23:59:59')</if>"
            + " ORDER BY u.user_id";

    /**
     * 分页查询用户（左连部门取部门名称）
     *
     * @param page    分页对象
     * @param q       查询条件
     * @param deptIds 数据权限限定的部门ID集合，为空表示不限
     * @return 分页结果
     */
    @Select("<script>" + USER_COLUMNS + USER_CONDITIONS + "</script>")
    IPage<User> selectUserPage(IPage<User> page, @Param("q") User q, @Param("deptIds") List<Long> deptIds);

    /**
     * 不分页查询用户（下拉、导出等场景）
     *
     * @param q       查询条件
     * @param deptIds 数据权限限定的部门ID集合，为空表示不限
     * @return 用户列表
     */
    @Select("<script>" + USER_COLUMNS + USER_CONDITIONS + "</script>")
    List<User> selectUserList(@Param("q") User q, @Param("deptIds") List<Long> deptIds);

    /**
     * 按账号查询用户（含密码，用于认证）
     *
     * @param username 登录账号
     * @return 用户
     */
    @Select("SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
            + "WHERE u.username = #{username} AND u.del_flag = 0 LIMIT 1")
    User selectUserByUsername(@Param("username") String username);

    /**
     * 批量新增用户角色关联
     *
     * @param userId  用户ID
     * @param roleIds 角色ID集合
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO sys_user_role(user_id, role_id) VALUES "
            + "<foreach collection=\"roleIds\" item=\"roleId\" separator=\",\">(#{userId}, #{roleId})</foreach>"
            + "</script>")
    int insertUserRole(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 按用户删除角色关联
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoleByUserId(@Param("userId") Long userId);

    /**
     * 批量删除多个用户的角色关联
     *
     * @param userIds 用户ID集合
     * @return 影响行数
     */
    @Delete("<script>DELETE FROM sys_user_role WHERE user_id IN "
            + "<foreach collection=\"userIds\" item=\"uid\" open=\"(\" separator=\",\" close=\")\">#{uid}</foreach>"
            + "</script>")
    int deleteUserRoleByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 批量新增用户岗位关联
     *
     * @param userId  用户ID
     * @param postIds 岗位ID集合
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO sys_user_post(user_id, post_id) VALUES "
            + "<foreach collection=\"postIds\" item=\"postId\" separator=\",\">(#{userId}, #{postId})</foreach>"
            + "</script>")
    int insertUserPost(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    /**
     * 按用户删除岗位关联
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_user_post WHERE user_id = #{userId}")
    int deleteUserPostByUserId(@Param("userId") Long userId);

    /**
     * 批量删除多个用户的岗位关联
     *
     * @param userIds 用户ID集合
     * @return 影响行数
     */
    @Delete("<script>DELETE FROM sys_user_post WHERE user_id IN "
            + "<foreach collection=\"userIds\" item=\"uid\" open=\"(\" separator=\",\" close=\")\">#{uid}</foreach>"
            + "</script>")
    int deleteUserPostByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计部门下的用户数量
     *
     * @param deptId 部门ID
     * @return 用户数量
     */
    @Select("SELECT COUNT(1) FROM sys_user WHERE del_flag = 0 AND dept_id = #{deptId}")
    int countUserByDeptId(@Param("deptId") Long deptId);

    /**
     * 查询已分配指定角色的用户数量
     *
     * @param roleId 角色ID
     * @return 用户数量
     */
    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId}")
    int countUserByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询用户已关联的角色ID集合（回显用）
     *
     * @param userId 用户ID
     * @return 角色ID集合
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户已关联的岗位ID集合（回显用）
     *
     * @param userId 用户ID
     * @return 岗位ID集合
     */
    @Select("SELECT post_id FROM sys_user_post WHERE user_id = #{userId}")
    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);
}
