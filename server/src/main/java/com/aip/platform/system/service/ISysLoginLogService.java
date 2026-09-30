package com.aip.platform.system.service;

import com.aip.platform.system.entity.LoginLog;

import java.util.List;

/**
 * 登录日志业务接口
 */
public interface ISysLoginLogService {

    /**
     * 记录一条登录日志
     *
     * @param username 登录账号
     * @param ipaddr   登录IP
     * @param browser  浏览器
     * @param os       操作系统
     * @param status   0 成功 1 失败
     * @param msg      提示消息
     */
    void record(String username, String ipaddr, String browser, String os, Integer status, String msg);

    /**
     * 写入一条登录日志
     *
     * @param log 登录日志
     */
    void saveLoginLog(LoginLog log);

    /**
     * 查询最近的登录日志
     *
     * @param limit 条数
     * @return 登录日志列表
     */
    List<LoginLog> selectRecent(int limit);
}
