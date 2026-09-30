package com.aip.platform.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.workflow.dto.GraphDef;
import com.aip.platform.workflow.engine.WorkflowEngine;
import com.aip.platform.workflow.entity.WfDefinition;
import com.aip.platform.workflow.entity.WfInstance;
import com.aip.platform.workflow.mapper.WfDefinitionMapper;
import com.aip.platform.workflow.service.IWfDefinitionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流定义服务实现
 */
@Slf4j
@Service
public class WfDefinitionServiceImpl extends ServiceImpl<WfDefinitionMapper, WfDefinition>
        implements IWfDefinitionService {

    private final ObjectMapper objectMapper;

    private final WorkflowEngine workflowEngine;

    /** 技能服务：用于读写工作流挂载的技能 */
    private final com.aip.platform.skill.service.ISkillService skillService;

    public WfDefinitionServiceImpl(ObjectMapper objectMapper, WorkflowEngine workflowEngine,
                                   com.aip.platform.skill.service.ISkillService skillService) {
        this.objectMapper = objectMapper;
        this.workflowEngine = workflowEngine;
        this.skillService = skillService;
    }

    @Override
    public PageResult<WfDefinition> selectPage(PageQuery query, WfDefinition def) {
        normalize(def);
        IPage<WfDefinition> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<WfDefinition> records = baseMapper.selectDefPage(page, def == null ? new WfDefinition() : def);
        // 分页插件只负责 count 与 LIMIT，记录回写由这里完成
        page.setRecords(records == null ? Collections.emptyList() : records);
        page.getRecords().forEach(this::fillSkills);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WfDefinition selectDetail(Long defId) {
        if (defId == null) {
            return null;
        }
        WfDefinition def = baseMapper.selectById(defId);
        fillGraph(def);
        fillSkills(def);
        return def;
    }

    /**
     * 回填工作流挂载的技能ID与名称
     */
    private void fillSkills(WfDefinition def) {
        if (def == null || def.getDefId() == null) {
            return;
        }
        try {
            List<Long> ids = skillService.selectSkillIdsByBiz(
                    com.aip.platform.skill.entity.SkillBinding.BizType.WORKFLOW, def.getDefId());
            def.setSkillIds(ids == null ? new ArrayList<Long>() : ids);
            if (ids != null && !ids.isEmpty()) {
                List<com.aip.platform.skill.entity.Skill> skills = skillService.selectByBiz(
                        com.aip.platform.skill.entity.SkillBinding.BizType.WORKFLOW, def.getDefId());
                if (skills != null && !skills.isEmpty()) {
                    def.setSkillNames(skills.stream()
                            .map(com.aip.platform.skill.entity.Skill::getSkillName)
                            .collect(Collectors.joining("、")));
                }
            }
        } catch (Exception e) {
            log.debug("加载工作流技能失败 defId={}: {}", def.getDefId(), e.getMessage());
        }
    }

    /** 保存工作流与技能的绑定关系 */
    private void saveSkillBinding(Long defId, List<Long> skillIds) {
        try {
            skillService.bindSkills(
                    com.aip.platform.skill.entity.SkillBinding.BizType.WORKFLOW, defId, skillIds);
        } catch (Exception e) {
            log.warn("保存工作流技能绑定失败 defId={}: {}", defId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDefinition(WfDefinition def) {
        if (def == null) {
            throw new BusinessException("流程定义不能为空");
        }
        normalize(def);
        if (StrUtil.isBlank(def.getDefName())) {
            throw new BusinessException("流程名称不能为空");
        }
        if (StrUtil.isBlank(def.getDefCode())) {
            def.setDefCode("wf_" + System.currentTimeMillis());
        }
        if (!checkCodeUnique(def)) {
            throw new BusinessException("流程编码[" + def.getDefCode() + "]已存在");
        }
        if (def.getStatus() == null) {
            def.setStatus(WfDefinition.STATUS_DRAFT);
        }
        if (def.getVersion() == null) {
            def.setVersion(1);
        }
        if (def.getPublished() == null) {
            def.setPublished(0);
        }
        if (def.getDeptId() == null) {
            def.setDeptId(SecurityUtils.getDeptId());
        }
        int rows = baseMapper.insert(def);
        saveSkillBinding(def.getDefId(), def.getSkillIds());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDefinition(WfDefinition def) {
        if (def == null || def.getDefId() == null) {
            throw new BusinessException("流程定义ID不能为空");
        }
        WfDefinition exists = baseMapper.selectById(def.getDefId());
        if (exists == null) {
            throw new BusinessException("流程定义不存在");
        }
        normalize(def);
        // 本次未传画布时保留原画布，避免把流程清空
        if (StrUtil.isBlank(def.getGraphJson())) {
            def.setGraphJson(exists.getGraphJson());
        }
        if (def.getDefCode() != null && !def.getDefCode().equals(exists.getDefCode())
                && !checkCodeUnique(def)) {
            throw new BusinessException("流程编码[" + def.getDefCode() + "]已存在");
        }
        int rows = baseMapper.updateById(def);
        // 传 null 表示本次不改动技能绑定，传空数组表示解除全部
        if (def.getSkillIds() != null) {
            saveSkillBinding(def.getDefId(), def.getSkillIds());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDefinitions(List<Long> defIds) {
        if (CollUtil.isEmpty(defIds)) {
            throw new BusinessException("请选择要删除的流程");
        }
        return baseMapper.deleteByIds(defIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long defId) {
        if (defId == null) {
            throw new BusinessException("流程定义ID不能为空");
        }
        WfDefinition exists = baseMapper.selectById(defId);
        if (exists == null) {
            throw new BusinessException("流程定义不存在");
        }
        WfDefinition update = new WfDefinition();
        update.setDefId(defId);
        update.setStatus(WfDefinition.STATUS_PUBLISHED);
        update.setPublished(1);
        update.setVersion(exists.getVersion() == null ? 1 : exists.getVersion() + 1);
        return baseMapper.updateById(update);
    }

    /**
     * 执行一次流程（刻意不加事务）
     *
     * <p>原因：执行期间要等待大模型返回，可能耗时数分钟，事务会长时间占用数据库连接；
     * 且失败实例本身也要落库保留现场，不能被回滚掉。引擎内部已消化所有异常。</p>
     */
    @Override
    public Map<String, Object> execute(Long defId, Map<String, Object> params) {
        if (defId == null) {
            throw new BusinessException("流程定义ID不能为空");
        }
        WfDefinition def = selectDetail(defId);
        if (def == null) {
            throw new BusinessException("流程定义不存在，defId=" + defId);
        }
        if (def.getStatus() != null && def.getStatus() == WfDefinition.STATUS_DISABLED) {
            throw new BusinessException("流程已停用，无法执行");
        }
        WfInstance instance = workflowEngine.run(def, params, WfInstance.TRIGGER_MANUAL);
        return buildRunResult(instance);
    }

    // ============================ 内部方法 ============================

    /** 组装执行结果：{instanceId, status, output, costMs} */
    private Map<String, Object> buildRunResult(WfInstance instance) {
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("instanceId", instance.getInstanceId());
        result.put("status", instance.getStatus());
        result.put("output", parseOutput(instance.getOutputData()));
        result.put("costMs", instance.getCostMs());
        if (instance.getErrorMsg() != null) {
            result.put("errorMsg", instance.getErrorMsg());
        }
        return result;
    }

    /** 出参还原：JSON 对象解成对象/数组，JSON 字符串解成字符串，失败则原样返回文本 */
    private Object parseOutput(String outputData) {
        if (StrUtil.isBlank(outputData)) {
            return null;
        }
        try {
            return objectMapper.readValue(outputData, Object.class);
        } catch (Exception ignored) {
            return outputData;
        }
    }

    /** 别名归一化：defKey -> defCode；graph -> graphJson */
    private void normalize(WfDefinition def) {
        if (def == null) {
            return;
        }
        if (StrUtil.isNotBlank(def.getDefKey()) && StrUtil.isBlank(def.getDefCode())) {
            def.setDefCode(def.getDefKey().trim());
        }
        if (StrUtil.isBlank(def.getDefKey()) && StrUtil.isNotBlank(def.getDefCode())) {
            def.setDefKey(def.getDefCode());
        }
        if (def.getGraph() != null) {
            try {
                def.setGraphJson(objectMapper.writeValueAsString(def.getGraph()));
            } catch (Exception e) {
                throw new BusinessException("画布序列化失败：" + e.getMessage());
            }
        }
    }

    /** 详情返回前把 graph_json 反序列化成 graph 对象 */
    private void fillGraph(WfDefinition def) {
        if (def == null) {
            return;
        }
        def.setDefKey(def.getDefCode());
        if (StrUtil.isBlank(def.getGraphJson())) {
            return;
        }
        try {
            def.setGraph(objectMapper.readValue(def.getGraphJson(), GraphDef.class));
        } catch (Exception e) {
            log.warn("流程[{}]画布解析失败，已按空画布返回：{}", def.getDefId(), e.getMessage());
            def.setGraph(new GraphDef());
        }
    }

    /** 流程编码唯一性校验 */
    private boolean checkCodeUnique(WfDefinition def) {
        if (def == null || StrUtil.isBlank(def.getDefCode())) {
            return true;
        }
        LambdaQueryWrapper<WfDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfDefinition::getDefCode, def.getDefCode());
        if (def.getDefId() != null) {
            wrapper.ne(WfDefinition::getDefId, def.getDefId());
        }
        return baseMapper.selectCount(wrapper) == 0;
    }
}
