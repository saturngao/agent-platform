package com.aip.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.Config;
import com.aip.platform.system.mapper.ConfigMapper;
import com.aip.platform.system.service.ISysConfigService;
import com.aip.platform.system.vo.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 系统参数业务实现
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<ConfigMapper, Config> implements ISysConfigService {

    /** 系统内置标记 */
    private static final int CONFIG_TYPE_BUILT_IN = 1;

    @Override
    public PageResult<Config> selectConfigPage(PageQuery query, Config config) {
        if (PageParam.isNoPaging(query)) {
            List<Config> all = selectConfigList(config);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<Config> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Config> result = baseMapper.selectPage(page, buildQueryWrapper(config));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<Config> selectConfigList(Config config) {
        return this.list(buildQueryWrapper(config));
    }

    @Override
    public Config selectConfigById(Long configId) {
        return configId == null ? null : baseMapper.selectById(configId);
    }

    @Override
    public String selectConfigValueByKey(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        return baseMapper.selectConfigValueByKey(key);
    }

    @Override
    public int setConfigValue(String key, String value) {
        if (StrUtil.isBlank(key)) {
            throw new BusinessException("参数键名不能为空");
        }
        LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Config::getConfigKey, key);
        Config exists = baseMapper.selectOne(wrapper);
        if (exists == null) {
            return 0;
        }
        Config update = new Config();
        update.setConfigId(exists.getConfigId());
        update.setConfigValue(value == null ? "" : value);
        return baseMapper.updateById(update);
    }

    @Override
    public int insertConfig(Config config) {
        if (config == null || StrUtil.isBlank(config.getConfigName()) || StrUtil.isBlank(config.getConfigKey())) {
            throw new BusinessException("参数名称与参数键名不能为空");
        }
        if (!checkConfigKeyUnique(config)) {
            throw new BusinessException("参数键名已存在");
        }
        if (config.getConfigType() == null) {
            config.setConfigType(0);
        }
        return baseMapper.insert(config);
    }

    @Override
    public int updateConfig(Config config) {
        if (config == null || config.getConfigId() == null) {
            throw new BusinessException("参数ID不能为空");
        }
        if (!checkConfigKeyUnique(config)) {
            throw new BusinessException("参数键名已存在");
        }
        return baseMapper.updateById(config);
    }

    @Override
    public int deleteConfigs(List<Long> configIds) {
        if (CollUtil.isEmpty(configIds)) {
            throw new BusinessException("请选择要删除的参数");
        }
        for (Config config : baseMapper.selectBatchIds(configIds)) {
            if (Objects.equals(CONFIG_TYPE_BUILT_IN, config.getConfigType())) {
                throw new BusinessException("内置参数不允许删除：" + config.getConfigName());
            }
        }
        return baseMapper.deleteBatchIds(configIds);
    }

    @Override
    public boolean checkConfigKeyUnique(Config config) {
        if (config == null || StrUtil.isBlank(config.getConfigKey())) {
            return true;
        }
        LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Config::getConfigKey, config.getConfigKey());
        if (config.getConfigId() != null) {
            wrapper.ne(Config::getConfigId, config.getConfigId());
        }
        return this.count(wrapper) == 0;
    }

    /** 构造参数查询条件 */
    private LambdaQueryWrapper<Config> buildQueryWrapper(Config config) {
        LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
        if (config != null) {
            if (StrUtil.isNotBlank(config.getConfigName())) {
                wrapper.like(Config::getConfigName, config.getConfigName());
            }
            if (StrUtil.isNotBlank(config.getConfigKey())) {
                wrapper.like(Config::getConfigKey, config.getConfigKey());
            }
            if (config.getConfigType() != null) {
                wrapper.eq(Config::getConfigType, config.getConfigType());
            }
        }
        wrapper.orderByAsc(Config::getConfigId);
        return wrapper;
    }
}
