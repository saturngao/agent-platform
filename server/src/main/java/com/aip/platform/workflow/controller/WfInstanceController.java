package com.aip.platform.workflow.controller;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.workflow.dto.WfInstanceQuery;
import com.aip.platform.workflow.entity.WfInstance;
import com.aip.platform.workflow.service.IWfInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作流运行实例接口
 */
@RestController
@RequestMapping("/api/workflow/instance")
@RequiredArgsConstructor
public class WfInstanceController {

    private final IWfInstanceService instanceService;

    /** 运行实例列表（分页） */
    @GetMapping({"", "/list"})
    public R<PageResult<WfInstance>> list(WfInstanceQuery query, PageQuery page) {
        return R.ok(instanceService.selectPage(page, query));
    }

    /** 实例详情 */
    @GetMapping("/{instanceId}")
    public R<WfInstance> detail(@PathVariable("instanceId") Long instanceId) {
        WfInstance instance = instanceService.selectDetail(instanceId);
        if (instance == null) {
            return R.fail("运行实例不存在");
        }
        return R.ok(instance);
    }

    /** 实例详情 + 节点执行明细（前端时间线展示） */
    @GetMapping("/{instanceId}/detail")
    public R<Map<String, Object>> nodeDetail(@PathVariable("instanceId") Long instanceId) {
        return R.ok(instanceService.selectFullDetail(instanceId));
    }

    /** 重跑实例 */
    @PreAuthorize("@permissionChecker.has('workflow:inst:run')")
    @PostMapping("/{instanceId}/retry")
    public R<Map<String, Object>> retry(@PathVariable("instanceId") Long instanceId) {
        return R.ok(instanceService.retry(instanceId));
    }

    /** 停止实例（异步 / 定时场景预留） */
    @PreAuthorize("@permissionChecker.has('workflow:inst:stop')")
    @PutMapping("/{instanceId}/stop")
    public R<Void> stop(@PathVariable("instanceId") Long instanceId) {
        instanceService.stop(instanceId);
        return R.ok();
    }

    /** 批量清空实例（ids 逗号分隔，连带删除节点记录） */
    @PreAuthorize("@permissionChecker.has('workflow:inst:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        instanceService.deleteInstances(ids);
        return R.ok();
    }
}
