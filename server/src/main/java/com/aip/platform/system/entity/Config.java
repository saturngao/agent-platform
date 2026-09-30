package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统参数配置（含邮件 SMTP、默认模型等）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class Config extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private String configName;

    private String configKey;

    private String configValue;

    /** 1 系统内置 */
    private Integer configType;
}
