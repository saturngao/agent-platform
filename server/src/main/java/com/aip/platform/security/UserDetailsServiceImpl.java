package com.aip.platform.security;

import com.aip.platform.common.Constants;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Spring Security 用户加载：从数据库读取账号并组装 LoginUser
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final ISysUserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.selectUserByUsername(username);
        if (user == null) {
            log.debug("账号不存在: {}", username);
            throw new UsernameNotFoundException("账号或密码错误");
        }
        if (Objects.equals(user.getDelFlag(), Constants.DEL_FLAG_DELETED)) {
            throw new DisabledException("账号已停用");
        }
        if (Objects.equals(user.getStatus(), Constants.STATUS_DISABLED)) {
            throw new DisabledException("账号已停用");
        }
        return userService.buildLoginUser(user);
    }
}
