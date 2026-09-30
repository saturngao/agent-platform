package com.aip.platform.work.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.Constants;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.llm.client.LlmClient;
import com.aip.platform.llm.client.LlmResponse;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysConfigService;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.PageParam;
import com.aip.platform.work.entity.WorkLog;
import com.aip.platform.work.entity.WorkSummary;
import com.aip.platform.work.entity.WorkTask;
import com.aip.platform.work.mapper.WorkLogMapper;
import com.aip.platform.work.mapper.WorkSummaryMapper;
import com.aip.platform.work.mapper.WorkTaskMapper;
import com.aip.platform.work.service.ISummaryService;
import com.aip.platform.work.util.MiniMarkdown;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 工作总结业务实现（模块核心）
 *
 * <p>主流程：校验周期 -> 确定统计对象 -> 取日志 -> 聚合成结构化文本 ->
 * 调用大模型（LlmClient）-> Markdown 转 HTML -> 落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl extends ServiceImpl<WorkSummaryMapper, WorkSummary> implements ISummaryService {

    /** 系统角色提示词：强调结构化、中文、Markdown、不编造 */
    private static final String SYSTEM_PROMPT = "你是一名专业的项目管理助理，擅长把零散的工作日志整理成结构化的工作报告。"
            + "请使用中文，输出 Markdown 格式，不要编造未提及的事实。";

    /** 输出要求：固定五段式结构，保证报告可直接投递给管理者 */
    private static final String USER_REQUIREMENT = "请基于以上工作日志原始数据，输出一份工作报告，严格按以下结构组织：\n"
            + "## 一、本期概览\n"
            + "用 3~5 句话概括本期整体情况：参与人员、日志条数、投入总工时、整体推进度。\n\n"
            + "## 二、关键进展\n"
            + "按人（或按任务）归纳本期取得的关键成果，每条包含：做了什么、当前进度、量化产出。"
            + "尽量使用无序列表，重要结论用 **加粗** 标注。\n\n"
            + "## 三、风险与阻塞\n"
            + "提炼日志中提到的阻塞项、延期风险与依赖关系；若没有明确阻塞，请写「本期未记录明显阻塞」，"
            + "并可根据进度与工时给出你的客观判断（注明这是推断）。\n\n"
            + "## 四、下期计划\n"
            + "汇总日志中的下一步计划，并按优先级排序，建议标明责任人与预期产出。\n\n"
            + "## 五、给管理者的建议\n"
            + "给出 3 条以内可执行的建议（资源协调、风险预案、流程改进等），语言简洁直接。\n\n"
            + "补充要求：\n"
            + "1. 只使用原始数据中出现的人物、任务、时间与数字，缺失的信息用「未记录」表述，不要虚构；\n"
            + "2. 适当引用负责人姓名与任务名，便于追溯；\n"
            + "3. 全文控制在 800 字以内，条理清晰、结论先行；\n"
            + "4. 不要输出代码块，不要重复原始数据全文。";

    /** 聚合文本长度上限，超过则按日志条数等比截断 */
    private static final int MAX_SOURCE_LENGTH = 12000;

    /** 统计周期最大跨度（天） */
    private static final int MAX_RANGE_DAYS = 366;

    /** 单条日志正文截断长度 */
    private static final int MAX_LOG_CONTENT = 300;

    /** 系统参数：总结默认模型 */
    private static final String KEY_SUMMARY_MODEL = "sys.summary.modelId";

    private final WorkLogMapper workLogMapper;

    private final WorkTaskMapper workTaskMapper;

    private final LlmClient llmClient;

    private final ISysConfigService configService;

    private final ISysUserService userService;

    /**
     * 说明：生成过程刻意不加 @Transactional。
     * 大模型调用耗时较长（数十秒），不应长时间占用数据库事务；
     * 且调用失败时需把 FAILED 记录落库供排查，若在事务中会被回滚掉。
     */
    @Override
    public WorkSummary generate(Long modelId, String scopeType, LocalDate begin, LocalDate end,
                                List<Long> memberIds, Long deptId, String title) {
        return doGenerate(new WorkSummary(), modelId, scopeType, begin, end, memberIds, deptId, title);
    }

    @Override
    public WorkSummary regenerate(Long summaryId) {
        if (summaryId == null) {
            throw new BusinessException("总结ID不能为空");
        }
        WorkSummary exists = baseMapper.selectById(summaryId);
        if (exists == null) {
            throw new BusinessException("总结不存在");
        }
        // 复用原记录的范围与时间；DEPT 范围只保存了成员ID，重新生成时按成员维度统计
        List<Long> memberIds = parseIds(exists.getTargetIds());
        return doGenerate(exists, exists.getModelId(), exists.getScopeType(), exists.getBeginDate(),
                exists.getEndDate(), memberIds, null, exists.getSummaryTitle());
    }

    @Override
    public PageResult<WorkSummary> selectPage(PageQuery query, WorkSummary summary) {
        WorkSummary condition = summary == null ? new WorkSummary() : summary;
        if (StrUtil.isNotBlank(condition.getSummaryType())) {
            condition.setScopeType(condition.getSummaryType().trim().toUpperCase(Locale.ROOT));
        }
        if (StrUtil.isBlank(condition.getSummaryTitle()) && StrUtil.isNotBlank(condition.getTitle())) {
            condition.setSummaryTitle(condition.getTitle());
        }

        boolean admin = SecurityUtils.isSuperAdmin();
        Long userId = SecurityUtils.getUserId();

        if (PageParam.isNoPaging(query)) {
            List<WorkSummary> all = baseMapper.selectSummaryList(condition, admin, userId);
            fillViewFields(all, false);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<WorkSummary> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<WorkSummary> result = baseMapper.selectSummaryPage(page, condition, admin, userId);
        // 列表不返回 HTML 与原始数据，避免响应体过大
        fillViewFields(result.getRecords(), true);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public WorkSummary selectById(Long summaryId) {
        if (summaryId == null) {
            throw new BusinessException("总结ID不能为空");
        }
        WorkSummary summary = baseMapper.selectById(summaryId);
        if (summary == null) {
            throw new BusinessException("总结不存在");
        }
        fillViewFields(summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSummaries(List<Long> summaryIds) {
        if (CollUtil.isEmpty(summaryIds)) {
            throw new BusinessException("请选择要删除的总结");
        }
        // work_summary 无 del_flag，做物理删除
        return baseMapper.deleteBatchIds(summaryIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markMailSent(Long summaryId, String mailTo) {
        if (summaryId == null) {
            throw new BusinessException("总结ID不能为空");
        }
        WorkSummary update = new WorkSummary();
        update.setSummaryId(summaryId);
        update.setMailSent(1);
        update.setMailTo(mailTo);
        update.setMailSentAt(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(update);
    }

    // ==================== 核心生成逻辑 ====================

    /**
     * 生成总结并落库
     *
     * @param entity    待写入的总结实体（已有ID时走更新，即重新生成）
     * @param modelId   模型ID
     * @param scopeType 统计范围
     * @param begin     起始日期
     * @param end       结束日期
     * @param memberIds 成员ID集合
     * @param deptId    部门ID
     * @param title     标题
     * @return 已落库的总结
     */
    private WorkSummary doGenerate(WorkSummary entity, Long modelId, String scopeType,
                                   LocalDate begin, LocalDate end, List<Long> memberIds,
                                   Long deptId, String title) {
        // 1. 校验时间范围
        if (begin == null || end == null) {
            throw new BusinessException("统计开始日期与结束日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(begin, end) > MAX_RANGE_DAYS) {
            throw new BusinessException("统计周期不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        String scope = normalizeScope(scopeType);

        // 2. 确定统计对象
        Long scopeDeptId = null;
        List<Long> userIds = new ArrayList<>();
        if ("DEPT".equals(scope)) {
            scopeDeptId = deptId != null ? deptId : SecurityUtils.getDeptId();
            if (scopeDeptId == null) {
                throw new BusinessException("无法确定统计部门，请指定部门");
            }
        } else if (CollUtil.isNotEmpty(memberIds)) {
            userIds.addAll(memberIds);
        } else {
            Long currentUserId = SecurityUtils.getUserId();
            if (currentUserId != null) {
                userIds.add(currentUserId);
            }
        }

        // 3. 取周期内日志
        List<WorkLog> logs = workLogMapper.selectForSummary(begin.toString(), end.toString(), userIds, scopeDeptId);
        if (CollUtil.isEmpty(logs)) {
            throw new BusinessException("所选时间范围内没有可总结的工作日志");
        }

        // 4. 聚合原始数据（含任务进展与长度保护）
        Map<Long, String> userNameMap = resolveUserNames(logs);
        Map<Long, WorkTask> taskMap = resolveTasks(logs);
        boolean[] truncated = new boolean[]{false};
        String sourceText = buildSourceText(logs, begin, end, userNameMap, taskMap, truncated);

        List<Long> logIds = logs.stream().map(WorkLog::getLogId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<Long> taskIds = new ArrayList<>(taskMap.keySet());

        // 5. 填充记录基础信息
        entity.setSummaryTitle(StrUtil.isBlank(title) ? defaultTitle(scope, begin, end) : title.trim());
        entity.setScopeType(scope);
        entity.setBeginDate(begin);
        entity.setEndDate(end);
        entity.setTargetIds(joinIds(CollUtil.isNotEmpty(userIds) ? userIds : new ArrayList<>(userNameMap.keySet())));
        entity.setSourceData(buildSourceJson(sourceText, logIds, taskIds, truncated[0]));
        entity.setLogCount(logs.size());
        entity.setPromptTpl(SYSTEM_PROMPT + "\n---\n" + USER_REQUIREMENT);
        entity.setStatus("GENERATING");
        entity.setErrorMsg(null);
        entity.setContentMd(null);
        entity.setContentHtml(null);
        entity.setTokens(0);
        entity.setCostMs(0);
        if (entity.getUserId() == null) {
            entity.setUserId(SecurityUtils.getUserId());
        }
        if (entity.getMailSent() == null) {
            entity.setMailSent(0);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setUpdateTime(LocalDateTime.now());

        // 6. 解析模型：入参 > 系统配置 > 默认模型（传 null 由 LlmClient 兜底）
        Long resolvedModelId = resolveModelId(modelId);
        LlmModel model;
        try {
            model = llmClient.getOrLoadModel(resolvedModelId);
        } catch (Exception e) {
            saveFailed(entity, e.getMessage());
            throw new BusinessException("AI 总结失败：" + e.getMessage());
        }
        entity.setModelId(model.getModelId());
        entity.setModelName(model.getModelName());

        // 7. 调用大模型
        String userPrompt = sourceText + "\n\n" + USER_REQUIREMENT;
        LlmResponse response;
        try {
            response = llmClient.chat(resolvedModelId, SYSTEM_PROMPT, userPrompt,
                    0.3, 2000, Constants.BizType.SUMMARY, null);
        } catch (Exception e) {
            saveFailed(entity, e.getMessage());
            throw new BusinessException("AI 总结失败：" + e.getMessage());
        }

        if (response == null || !response.isSuccess() || StrUtil.isBlank(response.getContent())) {
            String errorMsg = response == null ? "模型未返回任何结果" : response.getErrorMsg();
            saveFailed(entity, errorMsg);
            throw new BusinessException("AI 总结失败：" + errorMsg);
        }

        // 8. 成功：Markdown 转 HTML 后落库
        String markdown = response.getContent().trim();
        entity.setContentMd(markdown);
        entity.setContentHtml(MiniMarkdown.toHtml(markdown));
        entity.setTokens(response.getTotalTokens() == null ? 0 : response.getTotalTokens());
        entity.setCostMs(response.getCostMs() == null ? 0 : response.getCostMs());
        entity.setStatus("SUCCESS");
        entity.setErrorMsg(null);
        entity.setUpdateTime(LocalDateTime.now());

        if (entity.getSummaryId() == null) {
            baseMapper.insert(entity);
        } else {
            baseMapper.updateById(entity);
        }

        // 9. 标记日志已被总结引用
        markLogsSummarized(logs);

        fillViewFields(entity);
        return entity;
    }

    /** 失败落库：保留记录便于排查 */
    private void saveFailed(WorkSummary entity, String errorMsg) {
        entity.setStatus("FAILED");
        entity.setErrorMsg(abbreviate(StrUtil.isBlank(errorMsg) ? "未知错误" : errorMsg, 1800));
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        try {
            if (entity.getSummaryId() == null) {
                baseMapper.insert(entity);
            } else {
                baseMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.error("保存失败总结记录异常: {}", e.getMessage());
        }
    }

    // ==================== 原始数据聚合 ====================

    /**
     * 拼装喂给大模型的原始数据
     *
     * @param logs        日志
     * @param begin       起始日期
     * @param end         结束日期
     * @param userNameMap 用户ID -> 展示名
     * @param taskMap     任务ID -> 任务
     * @param truncated   出参：是否发生截断
     * @return 结构化文本
     */
    private String buildSourceText(List<WorkLog> logs, LocalDate begin, LocalDate end,
                                   Map<Long, String> userNameMap, Map<Long, WorkTask> taskMap,
                                   boolean[] truncated) {
        List<String> lines = new ArrayList<>();
        for (WorkLog log : logs) {
            lines.add(formatLogLine(log, userNameMap, taskMap));
        }
        int totalLength = 0;
        for (String line : lines) {
            totalLength += line.length();
        }

        List<String> keptLines = lines;
        if (totalLength > MAX_SOURCE_LENGTH && !lines.isEmpty()) {
            // 等比截断：按可容纳比例保留前 N 条日志
            int keep = (int) ((long) lines.size() * MAX_SOURCE_LENGTH / totalLength);
            keep = Math.max(1, Math.min(keep, lines.size()));
            keptLines = new ArrayList<>(lines.subList(0, keep));
            truncated[0] = true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【统计周期】").append(begin).append(" ~ ").append(end).append("\n");
        sb.append("【参与人员】").append(formatParticipants(logs, userNameMap)).append("\n");
        sb.append("【日志总数】").append(logs.size()).append(" 条\n");
        sb.append("【日志明细】\n");
        for (String line : keptLines) {
            sb.append(line).append("\n");
        }
        if (truncated[0]) {
            sb.append("（内容过长已截取，共 ").append(logs.size())
                    .append(" 条，已取前 ").append(keptLines.size()).append(" 条）\n");
        }
        sb.append("\n").append(buildTaskSection(taskMap, userNameMap));
        return sb.toString();
    }

    /** 单条日志格式化 */
    private String formatLogLine(WorkLog log, Map<Long, String> userNameMap, Map<Long, WorkTask> taskMap) {
        StringBuilder sb = new StringBuilder("· ");
        sb.append(log.getWorkDate() == null ? "日期未填" : log.getWorkDate()).append(" ");
        sb.append(userNameMap.getOrDefault(log.getUserId(), "未知成员")).append(" ");

        String title = StrUtil.isNotBlank(log.getLogTitle()) ? log.getLogTitle() : null;
        if (StrUtil.isBlank(title) && log.getTaskId() != null && taskMap.containsKey(log.getTaskId())) {
            title = taskMap.get(log.getTaskId()).getTaskTitle();
        }
        sb.append("[").append(StrUtil.isBlank(title) ? "未命名工作" : oneLine(title)).append("] ");
        sb.append(abbreviate(oneLine(log.getContent()), MAX_LOG_CONTENT));

        sb.append("（投入 ").append(log.getHoursSpent() == null ? "未记录" : log.getHoursSpent()).append("h")
                .append("，进度 ").append(log.getProgressSnap() == null ? "未记录" : log.getProgressSnap() + "%")
                .append("）");
        if (StrUtil.isNotBlank(log.getResult())) {
            sb.append(" 成果：").append(abbreviate(oneLine(log.getResult()), 120));
        }
        if (StrUtil.isNotBlank(log.getBlockers())) {
            sb.append("；阻塞：").append(abbreviate(oneLine(log.getBlockers()), 120));
        }
        if (StrUtil.isNotBlank(log.getPlanNext())) {
            sb.append("；下一步：").append(abbreviate(oneLine(log.getPlanNext()), 120));
        }
        return sb.toString();
    }

    /** 参与人员汇总：姓名(条数) */
    private String formatParticipants(List<WorkLog> logs, Map<Long, String> userNameMap) {
        Map<Long, Integer> counter = new LinkedHashMap<>();
        for (WorkLog log : logs) {
            if (log.getUserId() == null) {
                continue;
            }
            counter.put(log.getUserId(), counter.getOrDefault(log.getUserId(), 0) + 1);
        }
        if (counter.isEmpty()) {
            return "未记录";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : counter.entrySet()) {
            String name = userNameMap.getOrDefault(entry.getKey(), "未知成员");
            parts.add(name + "(" + entry.getValue() + "条)");
        }
        return String.join("、", parts);
    }

    /** 任务维度进展 */
    private String buildTaskSection(Map<Long, WorkTask> taskMap, Map<Long, String> userNameMap) {
        StringBuilder sb = new StringBuilder("【任务进展】\n");
        if (taskMap.isEmpty()) {
            sb.append("（本期日志未关联具体任务）\n");
            return sb.toString();
        }
        for (WorkTask task : taskMap.values()) {
            sb.append("· [").append(oneLine(task.getTaskTitle())).append("] 状态 ")
                    .append(statusText(task.getStatus()))
                    .append("，进度 ").append(task.getProgress() == null ? 0 : task.getProgress()).append("%")
                    .append("，负责人 ").append(task.getAssigneeId() == null ? "未指派"
                            : userNameMap.getOrDefault(task.getAssigneeId(), task.getAssigneeName()))
                    .append("，截止 ").append(task.getDueDate() == null ? "未设置" : task.getDueDate())
                    .append("\n");
        }
        return sb.toString();
    }

    /** 解析参与人员展示名 */
    private Map<Long, String> resolveUserNames(List<WorkLog> logs) {
        Map<Long, String> nameMap = new LinkedHashMap<>();
        for (WorkLog log : logs) {
            Long userId = log.getUserId();
            if (userId == null || nameMap.containsKey(userId)) {
                continue;
            }
            String name = log.getUsername();
            User user = userService.selectUserById(userId);
            if (user != null) {
                name = StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
            }
            nameMap.put(userId, StrUtil.isBlank(name) ? "用户" + userId : name);
        }
        return nameMap;
    }

    /** 批量取关联任务 */
    private Map<Long, WorkTask> resolveTasks(List<WorkLog> logs) {
        List<Long> taskIds = logs.stream().map(WorkLog::getTaskId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, WorkTask> taskMap = new LinkedHashMap<>();
        if (CollUtil.isEmpty(taskIds)) {
            return taskMap;
        }
        for (WorkTask task : workTaskMapper.selectBatchIds(taskIds)) {
            taskMap.put(task.getTaskId(), task);
        }
        return taskMap;
    }

    /** 原始数据快照 JSON */
    private String buildSourceJson(String text, List<Long> logIds, List<Long> taskIds, boolean truncated) {
        JSONObject json = JSONUtil.createObj();
        json.set("text", text);
        json.set("logIds", logIds);
        json.set("taskIds", taskIds);
        json.set("truncated", truncated);
        return json.toString();
    }

    /** 标记日志已被总结引用 */
    private void markLogsSummarized(List<WorkLog> logs) {
        for (WorkLog log : logs) {
            if (log.getLogId() == null) {
                continue;
            }
            WorkLog update = new WorkLog();
            update.setLogId(log.getLogId());
            update.setSummarized(1);
            workLogMapper.updateById(update);
        }
    }

    // ==================== 辅助方法 ====================

    /** 统计范围归一化为大写 */
    private String normalizeScope(String scopeType) {
        if (StrUtil.isBlank(scopeType)) {
            return "USER";
        }
        String scope = scopeType.trim().toUpperCase(Locale.ROOT);
        if ("USER".equals(scope) || "DEPT".equals(scope) || "TEAM".equals(scope)) {
            return scope;
        }
        throw new BusinessException("统计范围不合法，取值应为 USER / DEPT / TEAM");
    }

    /** 默认标题 */
    private String defaultTitle(String scope, LocalDate begin, LocalDate end) {
        String prefix = switch (scope) {
            case "DEPT" -> "部门工作总结";
            case "TEAM" -> "团队工作总结";
            default -> "个人工作总结";
        };
        return prefix + "（" + begin + " ~ " + end + "）";
    }

    /** 模型ID解析：入参 > 系统配置 > null（默认模型） */
    private Long resolveModelId(Long modelId) {
        if (modelId != null) {
            return modelId;
        }
        try {
            String value = configService.selectConfigValueByKey(KEY_SUMMARY_MODEL);
            if (StrUtil.isNotBlank(value) && value.trim().matches("\\d+")) {
                return Long.valueOf(value.trim());
            }
        } catch (Exception e) {
            log.warn("读取系统参数 {} 失败，使用默认模型: {}", KEY_SUMMARY_MODEL, e.getMessage());
        }
        return null;
    }

    /** 状态中文映射 */
    private String statusText(String status) {
        if (StrUtil.isBlank(status)) {
            return "未设置";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "TODO" -> "待开始";
            case "DOING" -> "进行中";
            case "REVIEW" -> "待评审";
            case "DONE" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    /** ID 集合转逗号分隔 */
    private String joinIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return "";
        }
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 逗号分隔字符串转 ID 集合 */
    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (StrUtil.isBlank(ids)) {
            return result;
        }
        for (String part : ids.split(",")) {
            String text = part.trim();
            if (text.matches("\\d+")) {
                result.add(Long.valueOf(text));
            }
        }
        return result;
    }

    /** 列表场景回填别名（可选裁剪大字段） */
    private void fillViewFields(List<WorkSummary> summaries, boolean slim) {
        if (CollUtil.isEmpty(summaries)) {
            return;
        }
        for (WorkSummary summary : summaries) {
            if (slim) {
                summary.setContentHtml(null);
                summary.setSourceData(null);
            }
            fillViewFields(summary);
        }
    }

    /** 回填前端别名字段 */
    private void fillViewFields(WorkSummary summary) {
        if (summary == null) {
            return;
        }
        summary.setTitle(summary.getSummaryTitle());
        summary.setContent(summary.getContentMd());
        summary.setSummaryType(summary.getScopeType());
        summary.setMemberIds(parseIds(summary.getTargetIds()));
    }

    /** 压缩为单行文本 */
    private String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /** 超长截断 */
    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
