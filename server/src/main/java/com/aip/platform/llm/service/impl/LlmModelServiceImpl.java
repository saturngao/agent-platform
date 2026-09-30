package com.aip.platform.llm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.llm.mapper.LlmModelMapper;
import com.aip.platform.llm.service.ILlmModelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型实例配置服务实现
 */
@Slf4j
@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements ILlmModelService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<LlmModel> selectPage(PageQuery query, LlmModel params) {
        normalize(params);
        List<LlmModel> list = baseMapper.selectModelList(params == null ? new LlmModel() : params);
        clearSecret(list);
        return PageResult.page(list, query.getPageNum(), query.getPageSize());
    }

    @Override
    public LlmModel selectByCode(String modelCode) {
        if (StrUtil.isBlank(modelCode)) {
            return null;
        }
        LlmModel model = getOne(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getModelCode, modelCode).last("LIMIT 1"));
        clearSecret(model);
        return model;
    }

    @Override
    public LlmModel selectDefaultModel() {
        LlmModel model = getOne(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getIsDefault, 1)
                .eq(LlmModel::getStatus, 0)
                .eq(LlmModel::getModelType, "chat")
                .last("LIMIT 1"));
        if (model == null) {
            model = getOne(new LambdaQueryWrapper<LlmModel>()
                    .eq(LlmModel::getStatus, 0)
                    .eq(LlmModel::getModelType, "chat")
                    .last("LIMIT 1"));
        }
        return model;
    }

    @Override
    public int insertModel(LlmModel model) {
        normalize(model);
        checkUnique(model);
        if (model.getModelType() == null || model.getModelType().isEmpty()) {
            model.setModelType("chat");
        }
        if (model.getTemperature() == null) {
            model.setTemperature(0.7D);
        }
        if (model.getTopP() == null) {
            model.setTopP(1.0D);
        }
        if (model.getMaxTokens() == null) {
            model.setMaxTokens(4096);
        }
        if (model.getTimeoutMs() == null) {
            model.setTimeoutMs(120000);
        }
        if (model.getSupportStream() == null) {
            model.setSupportStream(1);
        }
        if (model.getSupportJson() == null) {
            model.setSupportJson(0);
        }
        if (model.getStatus() == null) {
            model.setStatus(0);
        }
        if (model.getRateLimit() == null) {
            model.setRateLimit(0);
        }
        if (model.getContextWindow() == null) {
            model.setContextWindow(0);
        }
        return baseMapper.insert(model);
    }

    @Override
    public int updateModel(LlmModel model) {
        normalize(model);
        if (model.getModelId() == null) {
            throw new BusinessException("模型ID不能为空");
        }
        LlmModel exists = baseMapper.selectById(model.getModelId());
        if (exists == null) {
            throw new BusinessException("模型不存在");
        }
        // 入参为空时保留原值，避免部分更新把配置清空
        if (StrUtil.isBlank(model.getApiKey())) {
            model.setApiKey(exists.getApiKey());
        }
        if (StrUtil.isBlank(model.getEndpoint())) {
            model.setEndpoint(exists.getEndpoint());
        }
        if (StrUtil.isBlank(model.getModelNameId())) {
            model.setModelNameId(exists.getModelNameId());
        }
        if (model.getModelCode() != null && !model.getModelCode().equals(exists.getModelCode())) {
            checkUnique(model);
        }
        return baseMapper.updateById(model);
    }

    @Override
    public int deleteModels(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return baseMapper.deleteByIds(ids);
    }

    @Override
    public int setDefault(Long modelId) {
        LlmModel model = baseMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        LlmModel target = new LlmModel();
        target.setIsDefault(0);
        baseMapper.update(target, new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getIsDefault, 1));
        target.setModelId(modelId);
        target.setIsDefault(1);
        return baseMapper.updateById(target);
    }

    @Override
    public List<Map<String, Object>> selectChatOptions() {
        List<LlmModel> list = list(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getStatus, 0)
                .eq(LlmModel::getModelType, "chat")
                .orderByDesc(LlmModel::getIsDefault)
                .orderByAsc(LlmModel::getModelId));
        List<Map<String, Object>> options = new ArrayList<>();
        for (LlmModel m : list) {
            Map<String, Object> item = new HashMap<>(6);
            item.put("modelId", m.getModelId());
            item.put("modelCode", m.getModelCode());
            item.put("modelName", m.getModelName());
            item.put("modelNameId", m.getModelNameId());
            item.put("isDefault", m.getIsDefault());
            options.add(item);
        }
        return options;
    }

    /** 别名归一化：兼容前端提交的 modelKey / provider / camelCase 字段 */
    private void normalize(LlmModel model) {
        if (model == null) {
            return;
        }
        if (StrUtil.isNotBlank(model.getModelKey()) && StrUtil.isBlank(model.getModelCode())) {
            model.setModelCode(model.getModelKey());
        }
        if (StrUtil.isBlank(model.getModelCode()) && StrUtil.isNotBlank(model.getModelName())) {
            model.setModelCode(model.getModelName());
        }
        if (model.getProvider() != null && model.getProviderId() == null) {
            model.setProviderId(model.getProvider());
        }
        if (model.getEndpoint() != null) {
            String ep = model.getEndpoint().trim();
            // 允许只填 baseUrl，自动补全 chat 路径
            if (!ep.isEmpty() && !ep.endsWith("/chat/completions")) {
                ep = ep.replaceAll("/+$", "") + "/chat/completions";
            }
            model.setEndpoint(ep);
        }
        if (model.getModelNameId() == null || model.getModelNameId().isEmpty()) {
            if (model.getModelCode() != null && !model.getModelCode().isEmpty()) {
                model.setModelNameId(model.getModelCode());
            }
        }
    }

    private void checkUnique(LlmModel model) {
        LambdaQueryWrapper<LlmModel> wrapper = new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getModelCode, model.getModelCode());
        if (model.getModelId() != null) {
            wrapper.ne(LlmModel::getModelId, model.getModelId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("模型编码[" + model.getModelCode() + "]已存在");
        }
    }

    /** 列表返回时做密钥脱敏 */
    private void clearSecret(List<LlmModel> list) {
        if (list == null) {
            return;
        }
        for (LlmModel m : list) {
            clearSecret(m);
        }
    }

    private void clearSecret(LlmModel model) {
        if (model == null) {
            return;
        }
        String key = model.getApiKey();
        if (StrUtil.isNotBlank(key)) {
            int len = key.length();
            model.setApiKey(len <= 8 ? "******" : key.substring(0, 4) + "******" + key.substring(len - 4));
        }
    }
}
