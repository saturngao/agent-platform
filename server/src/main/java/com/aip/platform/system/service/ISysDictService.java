package com.aip.platform.system.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.DictData;
import com.aip.platform.system.entity.DictType;

import java.util.List;

/**
 * 字典业务接口：字典类型 + 字典数据
 */
public interface ISysDictService {

    /**
     * 分页查询字典类型
     *
     * @param query 分页参数
     * @param type  查询条件
     * @return 分页结果
     */
    PageResult<DictType> selectDictTypePage(PageQuery query, DictType type);

    /**
     * 不分页查询字典类型列表
     *
     * @param type 查询条件
     * @return 字典类型列表
     */
    List<DictType> selectDictTypeList(DictType type);

    /**
     * 按ID查询字典类型
     *
     * @param dictId 字典ID
     * @return 字典类型
     */
    DictType selectDictTypeById(Long dictId);

    /**
     * 新增字典类型
     *
     * @param type 字典类型
     * @return 影响行数
     */
    int insertDictType(DictType type);

    /**
     * 修改字典类型
     *
     * @param type 字典类型
     * @return 影响行数
     */
    int updateDictType(DictType type);

    /**
     * 批量删除字典类型（同时清理其字典数据）
     *
     * @param dictIds 字典ID集合
     * @return 影响行数
     */
    int deleteDictTypes(List<Long> dictIds);

    /**
     * 校验字典类型标识是否唯一
     *
     * @param type 字典类型
     * @return true 唯一
     */
    boolean checkDictTypeUnique(DictType type);

    /**
     * 分页查询字典数据
     *
     * @param query 分页参数
     * @param data  查询条件
     * @return 分页结果
     */
    PageResult<DictData> selectDictDataPage(PageQuery query, DictData data);

    /**
     * 不分页查询字典数据列表
     *
     * @param data 查询条件
     * @return 字典数据列表
     */
    List<DictData> selectDictDataList(DictData data);

    /**
     * 按字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<DictData> selectDictDataByType(String dictType);

    /**
     * 按编码查询字典数据
     *
     * @param dictCode 字典编码
     * @return 字典数据
     */
    DictData selectDictDataById(Long dictCode);

    /**
     * 新增字典数据
     *
     * @param data 字典数据
     * @return 影响行数
     */
    int insertDictData(DictData data);

    /**
     * 修改字典数据
     *
     * @param data 字典数据
     * @return 影响行数
     */
    int updateDictData(DictData data);

    /**
     * 批量删除字典数据
     *
     * @param dictCodes 字典编码集合
     * @return 影响行数
     */
    int deleteDictDatas(List<Long> dictCodes);
}
