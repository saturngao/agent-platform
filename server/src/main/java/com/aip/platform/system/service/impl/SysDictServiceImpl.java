package com.aip.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.DictData;
import com.aip.platform.system.entity.DictType;
import com.aip.platform.system.mapper.DictDataMapper;
import com.aip.platform.system.mapper.DictTypeMapper;
import com.aip.platform.system.service.ISysDictService;
import com.aip.platform.system.vo.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典业务实现：字典类型 + 字典数据
 */
@Service
public class SysDictServiceImpl extends ServiceImpl<DictTypeMapper, DictType> implements ISysDictService {

    private final DictDataMapper dictDataMapper;

    public SysDictServiceImpl(DictDataMapper dictDataMapper) {
        this.dictDataMapper = dictDataMapper;
    }

    @Override
    public PageResult<DictType> selectDictTypePage(PageQuery query, DictType type) {
        if (PageParam.isNoPaging(query)) {
            List<DictType> all = selectDictTypeList(type);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<DictType> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<DictType> result = baseMapper.selectPage(page, buildTypeWrapper(type));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<DictType> selectDictTypeList(DictType type) {
        return this.list(buildTypeWrapper(type));
    }

    @Override
    public DictType selectDictTypeById(Long dictId) {
        return dictId == null ? null : baseMapper.selectById(dictId);
    }

    @Override
    public int insertDictType(DictType type) {
        if (type == null || StrUtil.isBlank(type.getDictName()) || StrUtil.isBlank(type.getDictType())) {
            throw new BusinessException("字典名称与字典类型不能为空");
        }
        if (!checkDictTypeUnique(type)) {
            throw new BusinessException("字典类型已存在");
        }
        if (type.getStatus() == null) {
            type.setStatus(0);
        }
        return baseMapper.insert(type);
    }

    @Override
    public int updateDictType(DictType type) {
        if (type == null || type.getDictId() == null) {
            throw new BusinessException("字典ID不能为空");
        }
        if (!checkDictTypeUnique(type)) {
            throw new BusinessException("字典类型已存在");
        }
        return baseMapper.updateById(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDictTypes(List<Long> dictIds) {
        if (CollUtil.isEmpty(dictIds)) {
            throw new BusinessException("请选择要删除的字典");
        }
        List<DictType> types = baseMapper.selectBatchIds(dictIds);
        if (CollUtil.isNotEmpty(types)) {
            LambdaQueryWrapper<DictData> dataWrapper = new LambdaQueryWrapper<>();
            dataWrapper.in(DictData::getDictType, types.stream().map(DictType::getDictType).toList());
            dictDataMapper.delete(dataWrapper);
        }
        return baseMapper.deleteBatchIds(dictIds);
    }

    @Override
    public boolean checkDictTypeUnique(DictType type) {
        if (type == null || StrUtil.isBlank(type.getDictType())) {
            return true;
        }
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getDictType, type.getDictType());
        if (type.getDictId() != null) {
            wrapper.ne(DictType::getDictId, type.getDictId());
        }
        return this.count(wrapper) == 0;
    }

    @Override
    public PageResult<DictData> selectDictDataPage(PageQuery query, DictData data) {
        if (PageParam.isNoPaging(query)) {
            List<DictData> all = selectDictDataList(data);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<DictData> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<DictData> result = dictDataMapper.selectPage(page, buildDataWrapper(data));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<DictData> selectDictDataList(DictData data) {
        return dictDataMapper.selectList(buildDataWrapper(data));
    }

    @Override
    public List<DictData> selectDictDataByType(String dictType) {
        if (StrUtil.isBlank(dictType)) {
            return List.of();
        }
        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictData::getDictType, dictType)
                .eq(DictData::getStatus, 0)
                .orderByAsc(DictData::getDictSort);
        return dictDataMapper.selectList(wrapper);
    }

    @Override
    public DictData selectDictDataById(Long dictCode) {
        return dictCode == null ? null : dictDataMapper.selectById(dictCode);
    }

    @Override
    public int insertDictData(DictData data) {
        if (data == null || StrUtil.isBlank(data.getDictType()) || StrUtil.isBlank(data.getDictLabel())) {
            throw new BusinessException("字典类型与字典标签不能为空");
        }
        if (data.getDictSort() == null) {
            data.setDictSort(0);
        }
        if (data.getStatus() == null) {
            data.setStatus(0);
        }
        return dictDataMapper.insert(data);
    }

    @Override
    public int updateDictData(DictData data) {
        if (data == null || data.getDictCode() == null) {
            throw new BusinessException("字典编码不能为空");
        }
        return dictDataMapper.updateById(data);
    }

    @Override
    public int deleteDictDatas(List<Long> dictCodes) {
        if (CollUtil.isEmpty(dictCodes)) {
            throw new BusinessException("请选择要删除的字典数据");
        }
        return dictDataMapper.deleteBatchIds(dictCodes);
    }

    /** 构造字典类型查询条件 */
    private LambdaQueryWrapper<DictType> buildTypeWrapper(DictType type) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            if (StrUtil.isNotBlank(type.getDictName())) {
                wrapper.like(DictType::getDictName, type.getDictName());
            }
            if (StrUtil.isNotBlank(type.getDictType())) {
                wrapper.like(DictType::getDictType, type.getDictType());
            }
            if (type.getStatus() != null) {
                wrapper.eq(DictType::getStatus, type.getStatus());
            }
        }
        wrapper.orderByAsc(DictType::getDictId);
        return wrapper;
    }

    /** 构造字典数据查询条件 */
    private LambdaQueryWrapper<DictData> buildDataWrapper(DictData data) {
        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<>();
        if (data != null) {
            if (StrUtil.isNotBlank(data.getDictType())) {
                wrapper.eq(DictData::getDictType, data.getDictType());
            }
            if (StrUtil.isNotBlank(data.getDictLabel())) {
                wrapper.like(DictData::getDictLabel, data.getDictLabel());
            }
            if (StrUtil.isNotBlank(data.getDictValue())) {
                wrapper.eq(DictData::getDictValue, data.getDictValue());
            }
            if (data.getStatus() != null) {
                wrapper.eq(DictData::getStatus, data.getStatus());
            }
        }
        wrapper.orderByAsc(DictData::getDictSort);
        return wrapper;
    }
}
