package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.PageParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统用户管理接口
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final ISysUserService userService;

    /**
     * 用户列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<User>> list(User user, PageParam query) {
        return R.ok(userService.selectUserPage(query, user));
    }

    /**
     * 用户详情
     */
    @GetMapping("/detail/{userId}")
    public R<User> detail(@PathVariable("userId") Long userId) {
        User user = userService.selectUserById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        return R.ok(clearPassword(user));
    }

    /**
     * 新增用户
     */
    @PreAuthorize("@permissionChecker.has('system:user:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody User user) {
        userService.insertUser(user);
        return R.ok();
    }

    /**
     * 修改用户
     */
    @PreAuthorize("@permissionChecker.has('system:user:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody User user) {
        userService.updateUser(user);
        return R.ok();
    }

    /**
     * 批量删除用户（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:user:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        userService.deleteUsers(ids);
        return R.ok();
    }

    /**
     * 重置用户密码
     */
    @PreAuthorize("@permissionChecker.has('system:user:edit')")
    @PutMapping("/resetPwd")
    public R<Void> resetPwd(@RequestBody User user) {
        userService.resetPassword(user.getUserId(), user.getPassword());
        return R.ok();
    }

    /**
     * 修改用户状态
     */
    @PreAuthorize("@permissionChecker.has('system:user:edit')")
    @PutMapping("/status")
    public R<Void> status(@RequestBody User user) {
        userService.updateStatus(user.getUserId(), user.getStatus());
        return R.ok();
    }

    /**
     * 当前登录用户个人信息
     */
    @GetMapping("/profile")
    public R<User> profile() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return R.fail("请先登录");
        }
        User user = userService.selectUserById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        return R.ok(clearPassword(user));
    }

    /** 抹除密码，避免敏感信息外泄 */
    private User clearPassword(User user) {
        user.setPassword(null);
        return user;
    }
}
