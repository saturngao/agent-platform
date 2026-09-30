package com.aip.platform.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.aip.platform.common.R;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.common.constant.HttpStatus;
import com.aip.platform.system.entity.Dept;
import com.aip.platform.system.entity.Menu;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysDeptService;
import com.aip.platform.system.service.ISysLoginLogService;
import com.aip.platform.system.service.ISysMenuService;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.LoginBody;
import com.aip.platform.system.vo.LoginResultVO;
import com.aip.platform.system.vo.MetaVO;
import com.aip.platform.system.vo.RouterVO;
import com.aip.platform.system.vo.UserInfoVO;
import com.aip.platform.system.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证相关接口：登录、注销、用户信息、动态路由、验证码
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 登录状态：成功 */
    private static final int LOGIN_SUCCESS = 0;

    /** 登录状态：失败 */
    private static final int LOGIN_FAIL = 1;

    /** 未知客户端 */
    private static final String UNKNOWN = "未知";

    /** 顶层路由组件 */
    private static final String LAYOUT = "Layout";

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider jwtTokenProvider;

    private final ISysUserService userService;

    private final ISysMenuService menuService;

    private final ISysDeptService deptService;

    private final ISysLoginLogService loginLogService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public R<LoginResultVO> login(@RequestBody LoginBody body, HttpServletRequest request) {
        String username = body == null ? null : body.getUsername();
        String password = body == null ? null : body.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return R.fail(HttpStatus.BAD_REQUEST, "账号和密码不能为空");
        }
        String ip = getClientIp(request);
        UserAgent userAgent = parseUserAgent(request);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            loginUser.setLoginIp(ip);
            loginUser.setLoginTime(LocalDateTime.now());

            String token = jwtTokenProvider.generateToken(loginUser);
            userService.updateLoginInfo(loginUser.getUserId(), ip);
            loginLogService.record(username, ip, browserName(userAgent), osName(userAgent),
                    LOGIN_SUCCESS, "登录成功");

            User dbUser = userService.selectUserById(loginUser.getUserId());
            LoginResultVO result = LoginResultVO.builder()
                    .token(token)
                    .expiresIn(jwtTokenProvider.getExpireSeconds())
                    .userId(loginUser.getUserId())
                    .username(loginUser.getUsername())
                    .nickname(loginUser.getNickname())
                    .avatar(dbUser == null ? null : dbUser.getAvatar())
                    .deptId(loginUser.getDeptId())
                    .build();
            return R.ok("登录成功", result);
        } catch (DisabledException e) {
            loginLogService.record(username, ip, browserName(userAgent), osName(userAgent),
                    LOGIN_FAIL, "账号已停用");
            return R.fail(HttpStatus.UNAUTHORIZED, "账号已停用");
        } catch (BadCredentialsException e) {
            loginLogService.record(username, ip, browserName(userAgent), osName(userAgent),
                    LOGIN_FAIL, "账号或密码错误");
            return R.fail(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        } catch (AuthenticationException e) {
            loginLogService.record(username, ip, browserName(userAgent), osName(userAgent),
                    LOGIN_FAIL, e.getMessage());
            return R.fail(HttpStatus.UNAUTHORIZED, "登录失败：" + e.getMessage());
        }
    }

    /**
     * 当前登录用户基本信息、角色与权限
     */
    @GetMapping("/info")
    public R<UserInfoVO> info() {
        LoginUser loginUser = SecurityUtils.requireLoginUser();
        User dbUser = userService.selectUserById(loginUser.getUserId());
        UserProfileVO profile = UserProfileVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .avatar(dbUser == null ? null : dbUser.getAvatar())
                .email(loginUser.getEmail())
                .deptId(loginUser.getDeptId())
                .deptName(resolveDeptName(loginUser.getDeptId(), loginUser.getDeptName()))
                .build();
        List<String> roles = loginUser.getRoleKeys() == null ? new ArrayList<>() : new ArrayList<>(loginUser.getRoleKeys());
        List<String> permissions = loginUser.getPermissions() == null
                ? new ArrayList<>() : new ArrayList<>(loginUser.getPermissions());
        return R.ok(UserInfoVO.builder().user(profile).roles(roles).permissions(permissions).build());
    }

    /**
     * 当前用户可见的动态路由（菜单树转前端路由）
     */
    @GetMapping("/routers")
    public R<List<RouterVO>> routers() {
        LoginUser loginUser = SecurityUtils.requireLoginUser();
        List<Menu> menuTree = menuService.selectMenuTree(loginUser.getUserId());
        return R.ok(buildRouters(menuTree));
    }

    /**
     * 注销：清空安全上下文（JWT 无状态，前端丢弃令牌即可）
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        SecurityContextHolder.clearContext();
        return R.ok("退出成功");
    }

    /**
     * 验证码配置：当前未启用，保留扩展位
     */
    @GetMapping("/captcha")
    public R<Map<String, Object>> captcha() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captchaEnabled", Boolean.FALSE);
        data.put("code", HttpStatus.SUCCESS);
        data.put("uuid", "");
        data.put("img", null);
        return R.ok(data);
    }

    /** 菜单树转前端路由 */
    private List<RouterVO> buildRouters(List<Menu> menus) {
        List<RouterVO> routers = new ArrayList<>();
        if (menus == null) {
            return routers;
        }
        for (Menu menu : menus) {
            routers.add(toRouter(menu));
        }
        return routers;
    }

    /** 单个菜单转路由节点 */
    private RouterVO toRouter(Menu menu) {
        boolean topLevel = menu.getParentId() == null || menu.getParentId() == 0L;
        RouterVO router = new RouterVO();
        router.setId(menu.getMenuId());
        router.setName(buildRouteName(menu.getPath()));
        router.setPath(topLevel ? withSlash(menu.getPath()) : menu.getPath());
        // 顶层目录固定 Layout，其余原样返回数据库中的组件路径
        router.setComponent(topLevel && "M".equals(menu.getMenuType()) ? LAYOUT : menu.getComponent());
        router.setRedirect("M".equals(menu.getMenuType()) ? "noRedirect" : null);
        router.setHidden(menu.getVisible() != null && menu.getVisible() == 1);
        router.setAlwaysShow(false);
        router.setMeta(MetaVO.builder()
                .title(menu.getMenuName())
                .icon(menu.getIcon())
                .noCache(menu.getIsCache() != null && menu.getIsCache() == 1)
                .build());
        if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
            router.setChildren(buildRouters(menu.getChildren()));
        }
        return router;
    }

    /** 由路由地址推导路由名称：/system/user -&gt; User */
    private String buildRouteName(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        String trimmed = path.trim();
        int index = trimmed.lastIndexOf('/');
        String name = index >= 0 ? trimmed.substring(index + 1) : trimmed;
        if (StrUtil.isBlank(name)) {
            return trimmed;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /** 保证顶层路由地址以 / 开头 */
    private String withSlash(String path) {
        if (StrUtil.isBlank(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /** 解析部门名称，JWT 载荷中未携带时查库补全 */
    private String resolveDeptName(Long deptId, String deptName) {
        if (StrUtil.isNotBlank(deptName) || deptId == null) {
            return deptName;
        }
        Dept dept = deptService.selectDeptById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    /** 解析客户端 User-Agent */
    private UserAgent parseUserAgent(HttpServletRequest request) {
        String header = request.getHeader("User-Agent");
        if (StrUtil.isBlank(header)) {
            return null;
        }
        try {
            return UserAgentUtil.parse(header);
        } catch (Exception e) {
            return null;
        }
    }

    /** 浏览器名称 */
    private String browserName(UserAgent userAgent) {
        return userAgent == null || userAgent.getBrowser() == null ? UNKNOWN : userAgent.getBrowser().getName();
    }

    /** 操作系统名称 */
    private String osName(UserAgent userAgent) {
        return userAgent == null || userAgent.getOs() == null ? UNKNOWN : userAgent.getOs().getName();
    }

    /** 获取客户端真实IP */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StrUtil.isNotBlank(value) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
