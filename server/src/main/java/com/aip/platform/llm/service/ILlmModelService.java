package com.aip.platform.llm.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.llm.entity.LlmModel;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 模型实例配置服务
 */
public interface ILlmModelService extends IService<LlmModel> {

    /** 分页查询模型列表（自动补全供应商名称） */
    PageResult<LlmModel> selectPage(PageQuery query, LlmModel params);

    /** 按编码查询可用模型 */
    LlmModel selectByCode(String modelCode);

    /** 默认模型：优先 is_default=1，其次第一个启用的 chat 模型 */
    LlmModel selectDefaultModel();

    /** 新增（校验 modelCode 唯一，处理别名） */
    int insertModel(LlmModel model);

    /** 修改 */
    int updateModel(LlmModel model);

    /** 批量物理删除 */
    int deleteModels(List<Long> ids);

    /** 设为默认模型（同一时刻仅一个） */
    int setDefault(Long modelId);

    /** 下拉选项：所有可用对话模型 [{modelId, modelName, modelCode, providerName}] */
    List<Map<String, Object>> selectChatOptions();
}
