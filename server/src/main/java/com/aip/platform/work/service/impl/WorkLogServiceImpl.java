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
import com.aip.platform.work.entity.WorkLog;
import com.aip.platform.work.mapper.WorkLogMapper;
import com.aip.platform.work.service.IWorkLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作日志业务实现
 *
 * <p>数据权限说明（简化实现）：
 * 超级管理员可查看全部日志；普通用户只能看到「自己写的」或「本部门」的日志，
 * 过滤逻辑见 {@link WorkLogMapper#LOG_CONDITIONS} 的 {@code <if test="!isAdmin">} 分支。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkLogServiceImpl extends ServiceImpl<WorkLogMapper, WorkLog> implements IWorkLogService {

    private final ISysUserService userService;

    @Override
    public PageResult<WorkLog> selectPage(PageQuery query, WorkLog log) {
        WorkLog condition = log == null ? new WorkLog() : log;
        normalizeForQuery(condition);

        boolean admin = SecurityUtils.isSuperAdmin();
        Long userId = SecurityUtils.getUserId();
        Long deptId = SecurityUtils.getDeptId();

        if (PageParam.isNoPaging(query)) {
            List<WorkLog> all = baseMapper.selectLogList(condition, admin, userId, deptId);
            fillViewFields(all);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<WorkLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<WorkLog> result = baseMapper.selectLogPage(page, condition, admin, userId, deptId);
        fillViewFields(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public WorkLog selectById(Long logId) {
        if (logId == null) {
            throw new BusinessException("日志ID不能为空");
        }
        WorkLog log = baseMapper.selectById(logId);
        if (log == null) {
            throw new BusinessException("日志不存在或已被删除");
        }
        fillViewFields(log);
        return log;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertLog(WorkLog log) {
        if (log == null) {
            throw new BusinessException("日志参数不能为空");
        }
        normalizeForSave(log, true);

        Long currentUserId = SecurityUtils.getUserId();
        // 允许前端显式指定 userId（代填场景），否则取当前登录人
        if (log.getUserId() == null) {
            log.setUserId(currentUserId);
        }
        if (log.getUserId() != null && (currentUserId == null || !log.getUserId().equals(currentUserId))) {
            User owner = userService.selectUserById(log.getUserId());
            if (owner == null) {
                throw new BusinessException("指定的日志归属人不存在");
            }
            if (StrUtil.isBlank(log.getUsername())) {
                log.setUsername(owner.getUsername());
            }
            if (log.getDeptId() == null) {
                log.setDeptId(owner.getDeptId());
            }
        } else if (StrUtil.isBlank(log.getUsername())) {
            log.setUsername(SecurityUtils.getUsername());
        }
        if (log.getDeptId() == null) {
            log.setDeptId(SecurityUtils.getDeptId());
        }
        if (StrUtil.isBlank(log.getStatus())) {
            log.setStatus("NORMAL");
        }
        if (log.getSummarized() == null) {
            log.setSummarized(0);
        }
        if (log.getDelFlag() == null) {
            log.setDelFlag(0);
        }
        if (log.getWorkDate() == null) {
            log.setWorkDate(LocalDate.now());
        }
        if (StrUtil.isBlank(log.getLogType())) {
            log.setLogType("DAILY");
        }
        return baseMapper.insert(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLog(WorkLog log) {
        if (log == null || log.getLogId() == null) {
            throw new BusinessException("日志ID不能为空");
        }
        WorkLog exists = baseMapper.selectById(log.getLogId());
        if (exists == null) {
            throw new BusinessException("日志不存在或已被删除");
        }
        normalizeForSave(log, false);
        return baseMapper.updateById(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLogs(List<Long> logIds) {
        if (CollUtil.isEmpty(logIds)) {
            throw new BusinessException("请选择要删除的日志");
        }
        // @TableLogic 生效，实际执行的是 UPDATE del_flag = 2
        return baseMapper.deleteBatchIds(logIds);
    }

    @Override
    public PageResult<WorkLog> selectMyPage(PageQuery query, WorkLog log) {
        WorkLog condition = log == null ? new WorkLog() : log;
        condition.setUserId(SecurityUtils.getUserId());
        return selectPage(query, condition);
    }

    @Override
    public List<WorkLog> selectByRange(String begin, String end, Long userId) {
        WorkLog condition = new WorkLog();
        condition.setBeginTime(begin);
        condition.setEndTime(end);
        condition.setUserId(userId == null ? SecurityUtils.getUserId() : userId);
        boolean admin = SecurityUtils.isSuperAdmin();
        List<WorkLog> logs = baseMapper.selectLogList(condition, admin,
                SecurityUtils.getUserId(), SecurityUtils.getDeptId());
        fillViewFields(logs);
        return logs;
    }

    @Override
    public List<Map<String, Object>> selectDateTrend(int days) {
        int size = Math.max(days, 1);
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(size - 1L);

        boolean admin = SecurityUtils.isSuperAdmin();
        List<Map<String, Object>> rows = baseMapper.selectDateTrend(begin.toString(), admin,
                SecurityUtils.getUserId(), SecurityUtils.getDeptId());

        // 先补齐日期轴，再回填真实条数
        Map<String, Long> counter = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            counter.put(begin.plusDays(i).toString(), 0L);
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object key = row.get("workDate");
                Object value = row.get("cnt");
                if (key == null) {
                    continue;
                }
                long count = 0;
                if (value instanceof Number number) {
                    count = number.longValue();
                }
                counter.put(String.valueOf(key), count);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counter.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workDate", entry.getKey());
            item.put("cnt", entry.getValue());
            trend.add(item);
        }
        return trend;
    }

    // ==================== 私有方法 ====================

    /** 查询条件归一化 */
    private void normalizeForQuery(WorkLog log) {
        if (StrUtil.isNotBlank(log.getLogType())) {
            log.setLogType(log.getLogType().trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 保存/更新归一化：hours -> hoursSpent、progress -> progressSnap */
    private void normalizeForSave(WorkLog log, boolean requireContent) {
        if (log.getHours() != null) {
            log.setHoursSpent(log.getHours());
        }
        if (log.getProgress() != null) {
            log.setProgressSnap(log.getProgress());
        }
        if (requireContent && StrUtil.isBlank(log.getContent())) {
            throw new BusinessException("日志内容不能为空");
        }
        if (log.getProgressSnap() != null && (log.getProgressSnap() < 0 || log.getProgressSnap() > 100)) {
            throw new BusinessException("进度快照必须是 0~100 之间的整数");
        }
        if (StrUtil.isNotBlank(log.getLogType())) {
            log.setLogType(log.getLogType().trim().toUpperCase(Locale.ROOT));
        }
        if (StrUtil.isNotBlank(log.getStatus())) {
            log.setStatus(log.getStatus().trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 回填展示字段 */
    private void fillViewFields(List<WorkLog> logs) {
        if (CollUtil.isEmpty(logs)) {
            return;
        }
        for (WorkLog log : logs) {
            fillViewFields(log);
        }
    }

    private void fillViewFields(WorkLog log) {
        if (log == null) {
            return;
        }
        log.setHours(log.getHoursSpent());
        log.setProgress(log.getProgressSnap());
        if (StrUtil.isBlank(log.getDisplayName())) {
            log.setDisplayName(resolveDisplayName(log.getUserId(), log.getUsername()));
        }
    }

    /** 展示名：优先昵称，其次冗余的 username */
    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            User user = userService.selectUserById(userId);
            if (user != null) {
                return StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
            }
        }
        return fallback;
    }
}
