package com.aip.platform.work.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.vo.PageParam;
import com.aip.platform.work.entity.WorkLog;
import com.aip.platform.work.service.IWorkLogService;
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
 * 工作日志接口
 *
 * <p>REST 双映射兼容前端：{@code ""} 与 {@code /list}、{@code /add}、{@code /edit}、{@code /remove/{ids}} 均可访问。</p>
 */
@RestController
@RequestMapping("/api/work/log")
@RequiredArgsConstructor
public class WorkLogController {

    private final IWorkLogService logService;

    /**
     * 日志列表（分页）：支持 taskId / workDate / userId / keyword / beginTime / endTime
     */
    @GetMapping({"", "/list"})
    public R<PageResult<WorkLog>> list(WorkLog log, PageParam query) {
        return R.ok(logService.selectPage(query, log));
    }

    /**
     * 我的日志（分页）
     */
    @GetMapping({"/my", "/my/list"})
    public R<PageResult<WorkLog>> myList(WorkLog log, PageParam query) {
        return R.ok(logService.selectMyPage(query, log));
    }

    /**
     * 日志详情
     */
    @GetMapping({"/{logId}", "/detail/{logId}"})
    public R<WorkLog> detail(@PathVariable("logId") Long logId) {
        return R.ok(logService.selectById(logId));
    }

    /**
     * 按日期区间查询：?beginTime=yyyy-MM-dd&amp;endTime=yyyy-MM-dd&amp;userId=
     */
    @GetMapping("/range")
    public R<List<WorkLog>> range(String beginTime, String endTime, Long userId) {
        return R.ok(logService.selectByRange(beginTime, endTime, userId));
    }

    /**
     * 新增日志：body {taskId, workDate, hours, content, progress, logTitle, logType, result, blockers, planNext}
     */
    @PreAuthorize("@permissionChecker.has('work:log:add')")
    @PostMapping({"", "/add"})
    public R<WorkLog> add(@RequestBody WorkLog log) {
        logService.insertLog(log);
        return R.ok("新增成功", log);
    }

    /**
     * 修改日志
     */
    @PreAuthorize("@permissionChecker.has('work:log:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody WorkLog log) {
        logService.updateLog(log);
        return R.ok();
    }

    /**
     * 批量删除日志（逗号分隔，逻辑删除）
     */
    @PreAuthorize("@permissionChecker.has('work:log:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        logService.deleteLogs(ids);
        return R.ok();
    }

    /**
     * 最近 7 天日志条数趋势
     */
    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        List<Map<String, Object>> trend = logService.selectDateTrend(7);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", trend);
        long total = 0;
        if (trend != null) {
            for (Map<String, Object> row : trend) {
                Object value = row.get("cnt");
                if (value instanceof Number number) {
                    total += number.longValue();
                }
            }
        }
        data.put("total", total);
        return R.ok(data);
    }
}
