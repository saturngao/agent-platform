package com.aip.platform.work.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.system.entity.User;
import com.aip.platform.system.service.ISysUserService;
import com.aip.platform.system.vo.PageParam;
import com.aip.platform.work.entity.WorkTask;
import com.aip.platform.work.mapper.WorkTaskMapper;
import com.aip.platform.work.service.IWorkTaskService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作任务业务实现
 *
 * <p>数据权限说明（简化实现）：
 * 超级管理员可查看全部任务；普通用户只能看到「负责人是自己」或「归属本部门」的任务，
 * 该过滤在 {@link WorkTaskMapper#TASK_CONDITIONS} 的 {@code <if test="!isAdmin">} 分支中完成。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkTaskServiceImpl extends ServiceImpl<WorkTaskMapper, WorkTask> implements IWorkTaskService {

    /** 任务状态字典，统计时用于补零 */
    private static final String[] STATUS_ARRAY = {"TODO", "DOING", "REVIEW", "DONE", "CANCELLED"};

    private final ISysUserService userService;

    @Override
    public PageResult<WorkTask> selectPage(PageQuery query, WorkTask task) {
        WorkTask condition = task == null ? new WorkTask() : task;
        normalizeForQuery(condition);

        boolean admin = SecurityUtils.isSuperAdmin();
        Long userId = SecurityUtils.getUserId();
        Long deptId = SecurityUtils.getDeptId();

        if (PageParam.isNoPaging(query)) {
            List<WorkTask> all = baseMapper.selectTaskList(condition, admin, userId, deptId);
            fillViewFields(all);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<WorkTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<WorkTask> result = baseMapper.selectTaskPage(page, condition, admin, userId, deptId);
        fillViewFields(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public WorkTask selectById(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        WorkTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在或已被删除");
        }
        fillViewFields(task);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTask(WorkTask task) {
        if (task == null) {
            throw new BusinessException("任务参数不能为空");
        }
        normalizeForSave(task, true);

        if (StrUtil.isBlank(task.getStatus())) {
            task.setStatus("TODO");
        }
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        if (task.getParentId() == null) {
            task.setParentId(0L);
        }
        if (task.getDelFlag() == null) {
            task.setDelFlag(0);
        }
        // 指派人默认当前登录人
        Long currentUserId = SecurityUtils.getUserId();
        if (task.getAssignerId() == null) {
            task.setAssignerId(currentUserId);
        }
        // 负责人姓名冗余 + 部门兜底
        if (task.getAssigneeId() != null) {
            User assignee = userService.selectUserById(task.getAssigneeId());
            if (assignee != null) {
                if (StrUtil.isBlank(task.getAssigneeName())) {
                    task.setAssigneeName(displayName(assignee));
                }
                if (task.getDeptId() == null) {
                    task.setDeptId(assignee.getDeptId());
                }
            }
        }
        if (task.getDeptId() == null) {
            task.setDeptId(SecurityUtils.getDeptId());
        }
        return baseMapper.insert(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTask(WorkTask task) {
        if (task == null || task.getTaskId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        WorkTask exists = baseMapper.selectById(task.getTaskId());
        if (exists == null) {
            throw new BusinessException("任务不存在或已被删除");
        }
        normalizeForSave(task, false);
        // 负责人变化时同步冗余姓名
        if (task.getAssigneeId() != null
                && (StrUtil.isBlank(task.getAssigneeName()) || !task.getAssigneeId().equals(exists.getAssigneeId()))) {
            User assignee = userService.selectUserById(task.getAssigneeId());
            if (assignee != null) {
                task.setAssigneeName(displayName(assignee));
            }
        }
        if ("DONE".equals(task.getStatus()) && task.getFinishDate() == null) {
            task.setFinishDate(LocalDateTime.now());
        }
        return baseMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTasks(List<Long> taskIds) {
        if (CollUtil.isEmpty(taskIds)) {
            throw new BusinessException("请选择要删除的任务");
        }
        // @TableLogic 生效，实际执行的是 UPDATE del_flag = 2
        return baseMapper.deleteBatchIds(taskIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(Long taskId, String status) {
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        if (StrUtil.isBlank(status)) {
            throw new BusinessException("任务状态不能为空");
        }
        WorkTask task = new WorkTask();
        task.setTaskId(taskId);
        task.setStatus(status.trim().toUpperCase(Locale.ROOT));
        if ("DONE".equals(task.getStatus())) {
            task.setProgress(100);
            task.setFinishDate(LocalDateTime.now());
        }
        return baseMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProgress(Long taskId, Integer progress) {
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        if (progress == null || progress < 0 || progress > 100) {
            throw new BusinessException("进度必须是 0~100 之间的整数");
        }
        WorkTask task = new WorkTask();
        task.setTaskId(taskId);
        task.setProgress(progress);
        if (progress == 100) {
            task.setStatus("DONE");
            task.setFinishDate(LocalDateTime.now());
        }
        return baseMapper.updateById(task);
    }

    @Override
    public Map<String, Object> selectStatusStat() {
        boolean admin = SecurityUtils.isSuperAdmin();
        List<Map<String, Object>> rows =
                baseMapper.selectStatusStat(admin, SecurityUtils.getUserId(), SecurityUtils.getDeptId());
        Map<String, Object> stat = new LinkedHashMap<>();
        for (String status : STATUS_ARRAY) {
            stat.put(status, 0L);
        }
        long total = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object key = row.get("status");
                Object value = row.get("cnt");
                long count = 0;
                if (value instanceof Number number) {
                    count = number.longValue();
                }
                stat.put(key == null ? "UNKNOWN" : String.valueOf(key), count);
                total += count;
            }
        }
        stat.put("total", total);
        return stat;
    }

    // ==================== 私有方法 ====================

    /** 查询条件归一化：只做别名 -> 真实列 的映射，时间区间保持字符串语义 */
    private void normalizeForQuery(WorkTask task) {
        if (StrUtil.isNotBlank(task.getTaskName()) && StrUtil.isBlank(task.getTaskTitle())) {
            task.setTaskTitle(task.getTaskName());
        }
        if (StrUtil.isNotBlank(task.getAssignee())) {
            task.setAssigneeId(resolveUserId(task.getAssignee()));
        }
        if (StrUtil.isNotBlank(task.getStatus())) {
            task.setStatus(task.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StrUtil.isNotBlank(task.getPriority())) {
            task.setPriority(task.getPriority().trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 保存/更新归一化：前端别名映射到真实列，并做必要校验 */
    private void normalizeForSave(WorkTask task, boolean requireTitle) {
        if (StrUtil.isNotBlank(task.getTaskName())) {
            task.setTaskTitle(task.getTaskName());
        }
        if (StrUtil.isNotBlank(task.getContent())) {
            task.setTaskContent(task.getContent());
        }
        if (requireTitle && StrUtil.isBlank(task.getTaskTitle())) {
            throw new BusinessException("任务标题不能为空");
        }
        if (StrUtil.isNotBlank(task.getAssignee())) {
            task.setAssigneeId(resolveUserId(task.getAssignee()));
        }
        if (task.getStartDate() == null && StrUtil.isNotBlank(task.getStartTime())) {
            task.setStartDate(parseDate(task.getStartTime()));
        }
        if (task.getDueDate() == null && StrUtil.isNotBlank(task.getEndTime())) {
            task.setDueDate(parseDate(task.getEndTime()));
        }
        if (task.getStartDate() != null && task.getDueDate() != null
                && task.getStartDate().isAfter(task.getDueDate())) {
            throw new BusinessException("开始日期不能晚于截止日期");
        }
        // work_task 表没有 remark 列，统一追加到正文末尾（如需独立字段请加列后调整）
        if (StrUtil.isNotBlank(task.getRemark())) {
            String content = StrUtil.isBlank(task.getTaskContent()) ? "" : task.getTaskContent() + "\n";
            task.setTaskContent(content + "备注：" + task.getRemark().trim());
        }
        if (StrUtil.isNotBlank(task.getStatus())) {
            task.setStatus(task.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StrUtil.isNotBlank(task.getPriority())) {
            task.setPriority(task.getPriority().trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 兼容「负责人」入参：数字按用户ID，字符串按账号/昵称匹配 */
    private Long resolveUserId(String assignee) {
        String value = assignee.trim();
        if (value.matches("\\d+")) {
            return Long.valueOf(value);
        }
        User user = userService.selectUserByUsername(value);
        if (user == null) {
            User query = new User();
            query.setNickname(value);
            List<User> users = userService.selectUserList(query);
            if (CollUtil.isNotEmpty(users)) {
                user = users.get(0);
            }
        }
        if (user == null) {
            throw new BusinessException("未找到匹配的负责人：" + assignee);
        }
        return user.getUserId();
    }

    /** 日期解析兜底：兼容 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss、ISO 格式 */
    private LocalDate parseDate(String value) {
        String text = value.trim();
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            throw new BusinessException("日期格式不正确，应为 yyyy-MM-dd：" + value);
        }
    }

    /** 回填前端别名字段，保证列表/详情返回 taskName、content 等前端常用键 */
    private void fillViewFields(List<WorkTask> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        for (WorkTask task : tasks) {
            fillViewFields(task);
        }
    }

    private void fillViewFields(WorkTask task) {
        if (task == null) {
            return;
        }
        task.setTaskName(task.getTaskTitle());
        task.setContent(task.getTaskContent());
        task.setStartTime(task.getStartDate() == null ? null : task.getStartDate().toString());
        task.setEndTime(task.getDueDate() == null ? null : task.getDueDate().toString());
        task.setAssignee(task.getAssigneeId() == null ? null : String.valueOf(task.getAssigneeId()));
    }

    /** 展示名：优先昵称，其次账号 */
    private String displayName(User user) {
        return StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
