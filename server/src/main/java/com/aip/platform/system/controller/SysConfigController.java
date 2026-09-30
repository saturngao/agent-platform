package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.Config;
import com.aip.platform.system.service.ISysConfigService;
import com.aip.platform.system.vo.PageParam;
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

/**
 * 系统参数管理接口
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final ISysConfigService configService;

    /**
     * 参数列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<Config>> list(Config config, PageParam query) {
        return R.ok(configService.selectConfigPage(query, config));
    }

    /**
     * 参数详情（修改弹窗回显）
     */
    @GetMapping("/{configId}")
    public R<Config> detail(@PathVariable("configId") Long configId) {
        Config config = configService.selectConfigById(configId);
        if (config == null) {
            return R.fail("参数不存在");
        }
        return R.ok(config);
    }

    /**
     * 新增参数
     */
    @PreAuthorize("@permissionChecker.has('system:config:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody Config config) {
        configService.insertConfig(config);
        return R.ok();
    }

    /**
     * 修改参数
     */
    @PreAuthorize("@permissionChecker.has('system:config:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody Config config) {
        configService.updateConfig(config);
        return R.ok();
    }

    /**
     * 批量删除参数（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:config:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        configService.deleteConfigs(ids);
        return R.ok();
    }

    /**
     * 按参数键名查询参数值
     */
    @GetMapping("/configKey/{key}")
    public R<String> configKey(@PathVariable("key") String key) {
        return R.ok(configService.selectConfigValueByKey(key));
    }
}
