package com.aip.platform.system.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.Config;

import java.util.List;

/**
 * 系统参数业务接口
 */
public interface ISysConfigService {

    /**
     * 分页查询系统参数
     *
     * @param query  分页参数
     * @param config 查询条件
     * @return 分页结果
     */
    PageResult<Config> selectConfigPage(PageQuery query, Config config);

    /**
     * 不分页查询系统参数列表
     *
     * @param config 查询条件
     * @return 参数列表
     */
    List<Config> selectConfigList(Config config);

    /**
     * 按ID查询系统参数
     *
     * @param configId 参数ID
     * @return 系统参数
     */
    Config selectConfigById(Long configId);

    /**
     * 按参数键名查询参数值
     *
     * @param key 参数键名
     * @return 参数值，不存在返回 null
     */
    String selectConfigValueByKey(String key);

    /**
     * 设置参数值（不存在时返回 0）
     *
     * @param key   参数键名
     * @param value 参数值
     * @return 影响行数
     */
    int setConfigValue(String key, String value);

    /**
     * 新增系统参数
     *
     * @param config 系统参数
     * @return 影响行数
     */
    int insertConfig(Config config);

    /**
     * 修改系统参数
     *
     * @param config 系统参数
     * @return 影响行数
     */
    int updateConfig(Config config);

    /**
     * 批量删除系统参数（内置参数不允许删除）
     *
     * @param configIds 参数ID集合
     * @return 影响行数
     */
    int deleteConfigs(List<Long> configIds);

    /**
     * 校验参数键名是否唯一
     *
     * @param config 系统参数
     * @return true 唯一
     */
    boolean checkConfigKeyUnique(Config config);
}
