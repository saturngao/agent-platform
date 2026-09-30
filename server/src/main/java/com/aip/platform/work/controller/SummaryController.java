package com.aip.platform.work.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.PageParam;
import com.aip.platform.work.entity.WorkSummary;
import com.aip.platform.work.mail.MailService;
import com.aip.platform.work.service.ISummaryService;
import com.aip.platform.work.vo.MailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工作总结接口
 *
 * <p>生成、查询、重新生成、邮件推送。REST 双映射兼容前端。</p>
 */
@RestController
@RequestMapping("/api/work/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final ISummaryService summaryService;

    private final MailService mailService;

    private final ISysUserService userService;

    /**
     * 生成总结：body {summaryType, beginDate, endDate, memberIds:[], deptId, modelId, title}
     */
    @PreAuthorize("@permissionChecker.has('work:summary:add')")
    @PostMapping({"", "/generate"})
    public R<WorkSummary> generate(@RequestBody WorkSummary body) {
        if (body == null) {
            throw new BusinessException("请求参数不能为空");
        }
        WorkSummary summary = summaryService.generate(body.getModelId(), body.getSummaryType(),
                body.getBeginDate(), body.getEndDate(), body.getMemberIds(), body.getDeptId(), body.getTitle());
        return R.ok("生成成功", summary);
    }

    /**
     * 总结列表（分页）
     */
    @GetMapping({"", "/list"})
    public R<PageResult<WorkSummary>> list(WorkSummary summary, PageParam query) {
        return R.ok(summaryService.selectPage(query, summary));
    }

    /**
     * 总结详情（含 contentMd / contentHtml / sourceData）
     */
    @GetMapping({"/{summaryId}", "/detail/{summaryId}"})
    public R<WorkSummary> detail(@PathVariable("summaryId") Long summaryId) {
        return R.ok(summaryService.selectById(summaryId));
    }

    /**
     * 重新生成（覆盖同一条记录）
     */
    @PreAuthorize("@permissionChecker.has('work:summary:add')")
    @PostMapping({"/regenerate/{summaryId}", "/regen/{summaryId}"})
    public R<WorkSummary> regenerate(@PathVariable("summaryId") Long summaryId) {
        return R.ok("重新生成成功", summaryService.regenerate(summaryId));
    }

    /**
     * 批量删除总结
     */
    @PreAuthorize("@permissionChecker.has('work:summary:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        summaryService.deleteSummaries(ids);
        return R.ok();
    }

    /**
     * 邮件推送：body {summaryId, receivers:[], cc:[], subject}
     *
     * <p>receivers / cc 元素可以是邮箱地址，也可以是系统用户ID。</p>
     */
    @PreAuthorize("@permissionChecker.has('work:summary:mail')")
    @PostMapping("/mail")
    public R<Map<String, Object>> sendMail(@RequestBody MailRequest request) {
        if (request == null || request.getSummaryId() == null) {
            throw new BusinessException("总结ID不能为空");
        }
        WorkSummary summary = summaryService.selectById(request.getSummaryId());
        if (!"SUCCESS".equals(summary.getStatus())) {
            throw new BusinessException("仅生成成功的总结可以发送邮件");
        }

        List<String> to = resolveAddresses(request.getReceivers());
        List<String> cc = resolveAddresses(request.getCc());
        if (CollUtil.isEmpty(to)) {
            throw new BusinessException("请至少选择一位收件人");
        }
        String subject = StrUtil.isNotBlank(request.getSubject()) ? request.getSubject() : summary.getSummaryTitle();

        mailService.sendHtml(to.toArray(new String[0]),
                CollUtil.isEmpty(cc) ? null : cc.toArray(new String[0]),
                subject,
                buildMailHtml(summary));

        summaryService.markMailSent(summary.getSummaryId(), String.join(",", to));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sent", true);
        data.put("to", to);
        data.put("cc", cc);
        return R.ok("邮件已发送", data);
    }

    /**
     * 邮件服务是否已配置（供前端提示）
     */
    @GetMapping("/mail/status")
    public R<Map<String, Object>> mailStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", mailService.isConfigured());
        return R.ok(data);
    }

    /**
     * 可选成员下拉（总结范围 / 收件人选择用）
     */
    @GetMapping("/members")
    public R<List<Map<String, Object>>> members() {
        List<User> users = userService.selectUserList(new User());
        List<Map<String, Object>> list = new ArrayList<>();
        if (users != null) {
            for (User user : users) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("userId", user.getUserId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("email", user.getEmail());
                item.put("deptId", user.getDeptId());
                list.add(item);
            }
        }
        return R.ok(list);
    }

    // ==================== 私有方法 ====================

    /**
     * 解析收件人：邮箱地址直接使用；数字按用户ID查邮箱；其他按账号查邮箱
     */
    private List<String> resolveAddresses(List<String> rawList) {
        List<String> addresses = new ArrayList<>();
        if (CollUtil.isEmpty(rawList)) {
            return addresses;
        }
        for (String raw : rawList) {
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String value = raw.trim();
            if (value.contains("@")) {
                addresses.add(value);
                continue;
            }
            User user = value.matches("\\d+")
                    ? userService.selectUserById(Long.valueOf(value))
                    : userService.selectUserByUsername(value);
            if (user == null) {
                throw new BusinessException("未找到收件人：" + value);
            }
            if (StrUtil.isBlank(user.getEmail())) {
                throw new BusinessException("用户[" + displayName(user) + "]未配置邮箱，无法发送");
            }
            addresses.add(user.getEmail());
        }
        return addresses;
    }

    /** 邮件正文：简单排版的 HTML 外壳 */
    private String buildMailHtml(WorkSummary summary) {
        String markdown = summary.getContentMd() == null ? "" : summary.getContentMd();
        String body = StrUtil.isNotBlank(summary.getContentHtml())
                ? summary.getContentHtml()
                : "<pre>" + escapeHtml(markdown) + "</pre>";
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
                + "body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#24292f;"
                + "line-height:1.75;margin:0;padding:24px;background:#f6f8fa;}"
                + ".card{max-width:820px;margin:0 auto;background:#fff;border-radius:8px;padding:28px;"
                + "box-shadow:0 1px 3px rgba(0,0,0,.08);}"
                + "h1{font-size:20px;margin:0 0 8px;} h2{font-size:17px;margin:24px 0 8px;color:#1f2328;}"
                + "h3{font-size:15px;margin:18px 0 6px;}"
                + ".meta{color:#57606a;font-size:13px;margin-bottom:20px;padding-bottom:16px;border-bottom:1px solid #d0d7de;}"
                + "code{background:#f6f8fa;padding:2px 6px;border-radius:4px;font-size:13px;}"
                + "pre{background:#f6f8fa;padding:12px;border-radius:6px;overflow:auto;}"
                + "blockquote{margin:12px 0;padding:8px 16px;color:#57606a;border-left:3px solid #d0d7de;}"
                + ".footer{margin-top:28px;color:#8c959f;font-size:12px;}"
                + "</style></head><body><div class=\"card\">"
                + "<h1>" + escapeHtml(summary.getSummaryTitle() == null ? "AI 工作总结" : summary.getSummaryTitle()) + "</h1>"
                + "<div class=\"meta\">统计周期：" + summary.getBeginDate() + " ~ " + summary.getEndDate()
                + " ｜ 日志 " + (summary.getLogCount() == null ? 0 : summary.getLogCount()) + " 条"
                + " ｜ 生成模型：" + escapeHtml(summary.getModelName() == null ? "" : summary.getModelName()) + "</div>"
                + body
                + "<div class=\"footer\">本邮件由 AI 平台自动生成，如需调整请联系报告负责人。</div>"
                + "</div></body></html>";
    }

    /** 简单 HTML 转义，避免标题中的特殊字符破坏邮件结构 */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String displayName(User user) {
        return StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
