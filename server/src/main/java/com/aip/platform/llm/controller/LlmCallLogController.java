package com.aip.platform.llm.controller;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.llm.entity.LlmCallLog;
import com.aip.platform.llm.service.ILlmCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型调用日志接口
 */
@RestController
@RequestMapping("/api/llm/callLog")
@RequiredArgsConstructor
public class LlmCallLogController {

    private final ILlmCallLogService callLogService;

    @GetMapping({"", "/list"})
    public R<PageResult<LlmCallLog>> list(LlmCallLog params, PageQuery query,
                                          @RequestParam(required = false) String beginTime,
                                          @RequestParam(required = false) String endTime) {
        if (params == null) {
            params = new LlmCallLog();
        }
        return R.ok(callLogService.selectPage(query, params));
    }

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        Map<String, Object> data = new HashMap<>(4);
        data.put("overview", callLogService.statOverview());
        data.put("byBizType", callLogService.statByBizType());
        return R.ok(data);
    }

    @PreAuthorize("@permissionChecker.has('llm:model:remove')")
    @DeleteMapping("/clean")
    public R<Void> clean(@RequestParam(defaultValue = "30") int days) {
        callLogService.cleanBefore(days);
        return R.ok();
    }

    @GetMapping("/recent")
    public R<List<LlmCallLog>> recent(@RequestParam(defaultValue = "20") int limit) {
        PageQuery query = new PageQuery();
        query.setPageNum(1L);
        query.setPageSize((long) Math.min(limit, 200));
        return R.ok(callLogService.selectPage(query, new LlmCallLog()).getRows());
    }
}
