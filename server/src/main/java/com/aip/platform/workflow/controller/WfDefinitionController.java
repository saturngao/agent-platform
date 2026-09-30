package com.aip.platform.workflow.controller;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.workflow.entity.WfDefinition;
import com.aip.platform.workflow.service.IWfDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义接口
 *
 * <p>同时兼容 REST 风格（/api/workflow/def）与 /list、/add、/edit、/remove/{ids} 风格，
 * 避免前端两种写法对不上。</p>
 */
@RestController
@RequestMapping("/api/workflow/def")
@RequiredArgsConstructor
public class WfDefinitionController {

    private final IWfDefinitionService definitionService;

    /** 流程定义列表（分页） */
    @GetMapping({"", "/list"})
    public R<PageResult<WfDefinition>> list(WfDefinition def, PageQuery query) {
        return R.ok(definitionService.selectPage(query, def));
    }

    /** 流程定义详情（含画布 graph 对象） */
    @GetMapping("/{defId}")
    public R<WfDefinition> detail(@PathVariable("defId") Long defId) {
        WfDefinition def = definitionService.selectDetail(defId);
        if (def == null) {
            return R.fail("流程定义不存在");
        }
        return R.ok(def);
    }

    /** 新增流程定义 */
    @PreAuthorize("@permissionChecker.has('workflow:def:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody WfDefinition def) {
        definitionService.insertDefinition(def);
        return R.ok();
    }

    /** 修改流程定义 */
    @PreAuthorize("@permissionChecker.has('workflow:def:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody WfDefinition def) {
        definitionService.updateDefinition(def);
        return R.ok();
    }

    /** 批量删除流程定义（ids 逗号分隔） */
    @PreAuthorize("@permissionChecker.has('workflow:def:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        definitionService.deleteDefinitions(ids);
        return R.ok();
    }

    /** 发布流程 */
    @PreAuthorize("@permissionChecker.has('workflow:def:publish')")
    @PutMapping("/publish")
    public R<Void> publish(@RequestBody WfDefinition def) {
        if (def == null || def.getDefId() == null) {
            return R.fail("流程定义ID不能为空");
        }
        definitionService.publish(def.getDefId());
        return R.ok();
    }

    /** 试运行流程：body {defId, params:{}} */
    @PreAuthorize("@permissionChecker.has('workflow:def:run')")
    @PostMapping("/run")
    public R<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return R.fail("请求参数不能为空");
        }
        Long defId = toLong(body.get("defId"));
        if (defId == null) {
            return R.fail("流程定义ID不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        Object rawParams = body.get("params");
        if (rawParams instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                params.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return R.ok(definitionService.execute(defId, params));
    }

    /** 兼容前端可能传字符串形式的ID */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
