package com.aip.platform.skill.controller;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.skill.entity.Skill;
import com.aip.platform.skill.service.ISkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能模块接口
 *
 * <p>REST 双映射，兼容 /skill 与 /skill/add 两种写法，与系统其它模块保持一致。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;

    /** frontmatter 中的 name 字段 */
    private static final Pattern FM_NAME = Pattern.compile("(?m)^\\s*name\\s*[:：]\\s*(.+)$");
    /** frontmatter 中的 description 字段 */
    private static final Pattern FM_DESC = Pattern.compile("(?m)^\\s*description\\s*[:：]\\s*(.+)$");
    /** frontmatter 块：--- 开头与结尾 */
    private static final Pattern FM_BLOCK = Pattern.compile("^\\s*---\\s*\\n(.*?)\\n\\s*---\\s*\\n", Pattern.DOTALL);

    @GetMapping({"", "/list"})
    public R<PageResult<Skill>> list(Skill q, PageQuery query) {
        return R.ok(skillService.selectPage(query, q));
    }

    @GetMapping("/{skillId}")
    public R<Skill> detail(@PathVariable("skillId") Long skillId) {
        return R.ok(skillService.selectDetail(skillId));
    }

    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody Skill skill) {
        skillService.insertSkill(skill);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody Skill skill) {
        skillService.updateSkill(skill);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        skillService.deleteSkills(ids);
        return R.ok();
    }

    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PutMapping("/status")
    public R<Void> status(@RequestBody Skill skill) {
        skillService.changeStatus(skill.getSkillId(), skill.getStatus());
        return R.ok();
    }

    /** 下拉选项：只返回正常状态的技能 */
    @GetMapping("/optionselect")
    public R<List<Map<String, Object>>> optionselect() {
        return R.ok(skillService.selectOptions());
    }

    /** 查询某业务对象已挂载的技能ID */
    @GetMapping("/binding")
    public R<List<Long>> binding(@RequestParam("bizType") String bizType,
                                 @RequestParam("bizId") Long bizId) {
        return R.ok(skillService.selectSkillIdsByBiz(bizType, bizId));
    }

    /** 重建某业务对象的技能绑定 */
    @PreAuthorize("@permissionChecker.has('skill:def:edit')")
    @PostMapping("/binding")
    public R<Void> bind(@RequestBody Map<String, Object> body) {
        String bizType = body == null ? null : (String) body.get("bizType");
        Long bizId = body == null ? null : toLong(body.get("bizId"));
        List<Long> skillIds = body == null ? null : toLongList(body.get("skillIds"));
        skillService.bindSkills(bizType, bizId, skillIds);
        return R.ok();
    }

    /**
     * 导入技能：粘贴 SKILL.md 全文，自动解析 frontmatter 里的 name / description，
     * 解析不到时用默认值兜底。
     */
    @PreAuthorize("@permissionChecker.has('skill:def:add')")
    @PostMapping("/import")
    public R<Skill> importSkill(@RequestBody Map<String, Object> body) {
        if (body == null || StrUtil.isBlank((String) body.get("content"))) {
            return R.fail("技能内容不能为空");
        }
        String content = (String) body.get("content");
        String name = str(body.get("skillName"));
        String summary = str(body.get("summary"));

        // 解析 YAML frontmatter
        Matcher blockMatcher = FM_BLOCK.matcher(content);
        if (blockMatcher.find()) {
            String fm = blockMatcher.group(1);
            Matcher nm = FM_NAME.matcher(fm);
            if (nm.find() && StrUtil.isBlank(name)) {
                name = nm.group(1).trim();
            }
            Matcher dm = FM_DESC.matcher(fm);
            if (dm.find() && StrUtil.isBlank(summary)) {
                summary = dm.group(1).trim();
            }
        }
        if (StrUtil.isBlank(name)) {
            name = "导入技能-" + System.currentTimeMillis();
        }
        if (StrUtil.isBlank(summary)) {
            summary = StrUtil.isBlank(content) ? "" : content.trim().substring(0, Math.min(80, content.trim().length()));
        }

        // 编码：优先用传入值，否则按名称生成并保证唯一
        String code = str(body.get("skillCode"));
        if (StrUtil.isBlank(code)) {
            code = "import-" + UUID.randomUUID().toString().substring(0, 8);
        }

        Skill skill = new Skill();
        skill.setSkillCode(code);
        skill.setSkillName(name);
        skill.setSummary(summary);
        skill.setContent(content);
        skill.setSource(StrUtil.isBlank(str(body.get("source"))) ? "IMPORT" : str(body.get("source")));
        skill.setCategory(str(body.get("category")));
        skill.setIcon(StrUtil.isBlank(str(body.get("icon"))) ? "📦" : str(body.get("icon")));
        skill.setAuthor(StrUtil.isBlank(str(body.get("author"))) ? SecurityUtils.getUsername() : str(body.get("author")));
        skill.setVersion(StrUtil.isBlank(str(body.get("version"))) ? "1.0.0" : str(body.get("version")));
        skill.setStatus(0);

        skillService.insertSkill(skill);
        return R.ok(skill);
    }

    // ====================== 工具方法 ======================

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> toLongList(Object o) {
        List<Long> result = new ArrayList<>();
        if (o == null) {
            return result;
        }
        if (o instanceof List) {
            for (Object item : (List<Object>) o) {
                Long v = toLong(item);
                if (v != null) {
                    result.add(v);
                }
            }
        } else if (o instanceof String) {
            for (String part : ((String) o).split(",")) {
                if (StrUtil.isNotBlank(part)) {
                    result.add(Long.parseLong(part.trim()));
                }
            }
        }
        return result;
    }

    /** 预留：技能分类统计，供前端筛选展示 */
    @GetMapping("/categories")
    public R<List<Map<String, Object>>> categories() {
        List<String> cats = new ArrayList<>();
        cats.add("办公");
        cats.add("研发");
        cats.add("运维");
        cats.add("数据");
        cats.add("写作");
        cats.add("其他");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String c : cats) {
            Map<String, Object> m = new HashMap<>(2);
            m.put("label", c);
            m.put("value", c);
            list.add(m);
        }
        return R.ok(list);
    }
}
