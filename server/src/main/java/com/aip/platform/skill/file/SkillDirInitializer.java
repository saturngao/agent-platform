package com.aip.platform.skill.file;

import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.mapper.SkillMapper;
import com.aip.platform.skill.service.ISkillScriptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时为已有技能建立目录
 *
 * <p>文件化改造之前创建的技能只有数据库记录没有目录，这里在启动时补齐：
 * 保证每个技能都有对应的技能目录，并把早期存进数据库的脚本源码迁移成目录下的 .py 文件，
 * 让所有技能的脚本形态统一为「文件」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillDirInitializer implements ApplicationRunner {

    private final SkillMapper skillMapper;
    private final SkillFileService fileService;
    private final ISkillScriptService scriptService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Skill> skills = skillMapper.selectList(
                    new LambdaQueryWrapper<Skill>().isNotNull(Skill::getSkillCode));
            if (skills == null || skills.isEmpty()) {
                return;
            }
            int migrated = 0;
            for (Skill skill : skills) {
                try {
                    fileService.ensureDir(skill.getSkillCode());
                    ISkillScriptService.SyncResult res = scriptService.syncFromDirectory(skill.getSkillId());
                    if (res != null && !res.getMigrated().isEmpty()) {
                        migrated += res.getMigrated().size();
                    }
                } catch (Exception e) {
                    log.warn("初始化技能目录失败 {}：{}", skill.getSkillCode(), e.getMessage());
                }
            }
            log.info("技能目录初始化完成，共 {} 个技能，迁移内联脚本 {} 个", skills.size(), migrated);
        } catch (Exception e) {
            log.warn("技能目录初始化失败：{}", e.getMessage());
        }
    }
}
