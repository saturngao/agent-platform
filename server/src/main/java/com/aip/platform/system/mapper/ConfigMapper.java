package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.Config;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统参数数据访问
 */
public interface ConfigMapper extends BaseMapper<Config> {

    /**
     * 按参数键名查询参数值
     *
     * @param configKey 参数键名
     * @return 参数值，不存在返回 null
     */
    @Select("SELECT config_value FROM sys_config WHERE config_key = #{configKey} LIMIT 1")
    String selectConfigValueByKey(@Param("configKey") String configKey);
}
