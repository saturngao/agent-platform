package com.aip.platform.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.mapper.SkillBindingMapper;
import com.aip.platform.skill.mapper.SkillMapper;
import com.aip.platform.skill.service.ISkillService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements ISkillService {

    private final SkillBindingMapper bindingMapper;

    @Override
    public PageResult<Skill> selectPage(PageQuery query, Skill q) {
        IPage<Skill> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Skill> result = baseMapper.selectSkillPage(page, q == null ? new Skill() : q);
        List<Skill> rows = result.getRecords();
        if (rows != null) {
            rows.forEach(this::fillTagList);
        }
        return PageResult.of(rows, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public Skill selectDetail(Long skillId) {
        if (skillId == null) {
            throw new BusinessException("技能ID不能为空");
        }
        Skill skill = baseMapper.selectById(skillId);
        if (skill != null) {
            fillTagList(skill);
        }
        return skill;
    }

    @Override
    public Skill selectByCode(String skillCode) {
        if (StrUtil.isBlank(skillCode)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Skill>().eq(Skill::getSkillCode, skillCode), false);
    }

    @Override
    public int insertSkill(Skill skill) {
        validate(skill, true);
        skill.setSkillId(null);
        if (skill.getStatus() == null) {
            skill.setStatus(0);
        }
        if (StrUtil.isBlank(skill.getSource())) {
            skill.setSource("CUSTOM");
        }
        if (StrUtil.isBlank(skill.getVersion())) {
            skill.setVersion("1.0.0");
        }
        if (StrUtil.isBlank(skill.getIcon())) {
            skill.setIcon("🧩");
        }
        normalizeTags(skill);
        return baseMapper.insert(skill);
    }

    @Override
    public int updateSkill(Skill skill) {
        validate(skill, false);
        normalizeTags(skill);
        return baseMapper.updateById(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSkills(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        for (Long id : skillIds) {
            // 官方内置技能不允许删除，避免误删后内置市场数据缺失
            Skill exist = baseMapper.selectById(id);
            if (exist != null && "OFFICIAL".equals(exist.getSource())) {
                throw new BusinessException("内置官方技能不可删除，可将其停用");
            }
            // 同时解除该技能在所有智能体/工作流上的挂载
            bindingMapper.deleteBySkillId(id);
        }
        return baseMapper.deleteBatchIds(skillIds);
    }

    @Override
    public int changeStatus(Long skillId, Integer status) {
        Skill skill = new Skill();
        skill.setSkillId(skillId);
        skill.setStatus(status);
        return baseMapper.updateById(skill);
    }

    @Override
    public List<Map<String, Object>> selectOptions() {
        List<Skill> list = list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getStatus, 0)
                .orderByAsc(Skill::getSkillId));
        List<Map<String, Object>> options = new ArrayList<>();
        for (Skill s : list) {
            Map<String, Object> item = new HashMap<>(6);
            item.put("skillId", s.getSkillId());
            item.put("skillCode", s.getSkillCode());
            item.put("skillName", s.getSkillName());
            item.put("category", s.getCategory());
            item.put("icon", s.getIcon());
            item.put("summary", s.getSummary());
            options.add(item);
        }
        return options;
    }

    @Override
    public List<Skill> selectByBiz(String bizType, Long bizId) {
        if (StrUtil.isBlank(bizType) || bizId == null) {
            return Collections.emptyList();
        }
        return baseMapper.selectByBiz(bizType, bizId);
    }

    @Override
    public List<Long> selectSkillIdsByBiz(String bizType, Long bizId) {
        if (StrUtil.isBlank(bizType) || bizId == null) {
            return Collections.emptyList();
        }
        return bindingMapper.selectSkillIdsByBiz(bizType, bizId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSkills(String bizType, Long bizId, List<Long> skillIds) {
        if (StrUtil.isBlank(bizType) || bizId == null) {
            return;
        }
        // 先清空旧绑定，再按新列表重建，保证"取消勾选"能生效
        bindingMapper.deleteByBiz(bizType, bizId);
        if (skillIds == null || skillIds.isEmpty()) {
            return;
        }
        List<Long> valid = skillIds.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        if (!valid.isEmpty()) {
            bindingMapper.insertBatch(valid, bizType, bizId, SecurityUtils.getUsername());
        }
    }

    @Override
    public String buildSkillPrompt(String bizType, Long bizId) {
        List<Skill> skills = selectByBiz(bizType, bizId);
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 可用技能\n");
        sb.append("以下是你可以使用的技能，当任务匹配某个技能的适用场景时，请严格按该技能的要求输出：\n\n");
        for (Skill s : skills) {
            sb.append("## 技能：").append(s.getSkillName()).append("\n");
            if (StrUtil.isNotBlank(s.getSummary())) {
                sb.append("> ").append(s.getSummary()).append("\n\n");
            }
            if (StrUtil.isNotBlank(s.getContent())) {
                sb.append(s.getContent()).append("\n\n");
            }
        }
        sb.append("# 技能使用说明结束\n");
        return sb.toString();
    }

    // ====================== 私有方法 ======================

    /** 校验：名称、编码必填且编码唯一 */
    private void validate(Skill skill, boolean isInsert) {
        if (skill == null) {
            throw new BusinessException("技能信息不能为空");
        }
        if (StrUtil.isBlank(skill.getSkillName())) {
            throw new BusinessException("技能名称不能为空");
        }
        if (StrUtil.isBlank(skill.getSkillCode())) {
            throw new BusinessException("技能编码不能为空");
        }
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                .eq(Skill::getSkillCode, skill.getSkillCode());
        if (!isInsert) {
            wrapper.ne(Skill::getSkillId, skill.getSkillId());
        }
        if (count(wrapper) > 0) {
            throw new BusinessException("技能编码「" + skill.getSkillCode() + "」已存在");
        }
        if (StrUtil.isBlank(skill.getContent())) {
            throw new BusinessException("技能内容不能为空");
        }
    }

    /** 标签列表与逗号分隔字符串互转 */
    private void normalizeTags(Skill skill) {
        if (skill.getTagList() != null && !skill.getTagList().isEmpty()) {
            skill.setTags(String.join(",", skill.getTagList()));
        }
    }

    /** 回显时把 tags 拆成数组 */
    private void fillTagList(Skill skill) {
        if (skill == null) {
            return;
        }
        if (StrUtil.isNotBlank(skill.getTags())) {
            skill.setTagList(Arrays.stream(skill.getTags().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        } else {
            skill.setTagList(Collections.emptyList());
        }
    }
}
