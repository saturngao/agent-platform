package com.aip.platform.work.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.vo.PageParam;
import com.aip.platform.work.entity.WorkTask;
import com.aip.platform.work.service.IWorkTaskService;
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

import java.util.List;
import java.util.Map;

/**
 * 工作任务接口
 *
 * <p>REST 双映射兼容前端：{@code ""} 与 {@code /list}、{@code /add}、{@code /edit}、{@code /remove/{ids}} 均可访问。</p>
 */
@RestController
@RequestMapping("/api/work/task")
@RequiredArgsConstructor
public class WorkTaskController {

    private final IWorkTaskService taskService;

    /**
     * 任务列表（分页）
     */
    @GetMapping({"", "/list"})
    public R<PageResult<WorkTask>> list(WorkTask task, PageParam query) {
        return R.ok(taskService.selectPage(query, task));
    }

    /**
     * 任务详情
     */
    @GetMapping({"/{taskId}", "/detail/{taskId}"})
    public R<WorkTask> detail(@PathVariable("taskId") Long taskId) {
        return R.ok(taskService.selectById(taskId));
    }

    /**
     * 新增任务
     */
    @PreAuthorize("@permissionChecker.has('work:task:add')")
    @PostMapping({"", "/add"})
    public R<WorkTask> add(@RequestBody WorkTask task) {
        taskService.insertTask(task);
        return R.ok("新增成功", task);
    }

    /**
     * 修改任务
     */
    @PreAuthorize("@permissionChecker.has('work:task:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody WorkTask task) {
        taskService.updateTask(task);
        return R.ok();
    }

    /**
     * 批量删除任务（逗号分隔，逻辑删除）
     */
    @PreAuthorize("@permissionChecker.has('work:task:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        taskService.deleteTasks(ids);
        return R.ok();
    }

    /**
     * 修改任务状态：body {taskId, status}
     */
    @PreAuthorize("@permissionChecker.has('work:task:edit')")
    @PutMapping("/status")
    public R<Void> status(@RequestBody WorkTask task) {
        taskService.updateStatus(task.getTaskId(), task.getStatus());
        return R.ok();
    }

    /**
     * 修改任务进度：body {taskId, progress}
     */
    @PreAuthorize("@permissionChecker.has('work:task:edit')")
    @PutMapping("/progress")
    public R<Void> progress(@RequestBody WorkTask task) {
        taskService.updateProgress(task.getTaskId(), task.getProgress());
        return R.ok();
    }

    /**
     * 任务状态分布统计（看板用）
     */
    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(taskService.selectStatusStat());
    }
}
