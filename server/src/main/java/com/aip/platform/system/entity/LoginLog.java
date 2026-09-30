package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统登录日志
 *
 * <p>对应表 sys_login_log，该表只有审计时间字段 login_time，
 * 没有 create_by / remark 等列，因此不继承 BaseEntity。</p>
 */
@Data
@TableName("sys_login_log")
public class LoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问ID */
    @TableId(value = "info_id", type = IdType.AUTO)
    private Long infoId;

    /** 登录账号 */
    private String username;

    /** 登录IP */
    private String ipaddr;

    /** 浏览器类型 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录状态：0 成功 1 失败 */
    private Integer status;

    /** 提示消息 */
    private String msg;

    /** 访问时间 */
    private LocalDateTime loginTime;
}
