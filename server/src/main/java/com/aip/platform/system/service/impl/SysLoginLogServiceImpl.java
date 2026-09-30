package com.aip.platform.system.service.impl;

import com.aip.platform.system.entity.LoginLog;
import com.aip.platform.system.mapper.LoginLogMapper;
import com.aip.platform.system.service.ISysLoginLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志业务实现
 */
@Slf4j
@Service
public class SysLoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements ISysLoginLogService {

    @Override
    public void record(String username, String ipaddr, String browser, String os, Integer status, String msg) {
        LoginLog loginLog = new LoginLog();
        loginLog.setUsername(username);
        loginLog.setIpaddr(ipaddr);
        loginLog.setBrowser(browser);
        loginLog.setOs(os);
        loginLog.setStatus(status);
        loginLog.setMsg(msg);
        loginLog.setLoginTime(LocalDateTime.now());
        saveLoginLog(loginLog);
    }

    @Override
    public void saveLoginLog(LoginLog loginLog) {
        try {
            baseMapper.insert(loginLog);
        } catch (Exception e) {
            // 登录日志写入失败不应影响主流程
            log.warn("写入登录日志失败: {}", e.getMessage());
        }
    }

    @Override
    public List<LoginLog> selectRecent(int limit) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(LoginLog::getLoginTime);
        Page<LoginLog> page = new Page<>(1, limit <= 0 ? 10 : limit);
        page.setSearchCount(false);
        return baseMapper.selectPage(page, wrapper).getRecords();
    }
}
