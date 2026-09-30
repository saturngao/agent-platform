package com.aip.platform.work.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.work.entity.WorkSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 工作总结业务接口
 */
public interface ISummaryService {

    /**
     * 生成工作总结
     *
     * <p>流程：校验周期 -> 确定统计对象 -> 取日志并聚合成结构化文本 -> 调用大模型 -> 落库。</p>
     *
     * @param modelId   模型ID，为空时取系统配置 sys.summary.modelId，仍为空则使用默认模型
     * @param scopeType 统计范围 USER / DEPT / TEAM（大小写不敏感）
     * @param begin     起始日期
     * @param end       结束日期
     * @param memberIds 指定成员ID集合，为空时按 scopeType 兜底
     * @param deptId    部门ID（DEPT 范围用，为空取当前登录人部门）
     * @param title     报告标题，为空自动生成
     * @return 已落库的总结（含 contentMd）
     */
    WorkSummary generate(Long modelId, String scopeType, LocalDate begin, LocalDate end,
                         List<Long> memberIds, Long deptId, String title);

    /**
     * 重新生成（复用原记录的参数并更新同一条记录）
     *
     * @param summaryId 总结ID
     * @return 更新后的总结
     */
    WorkSummary regenerate(Long summaryId);

    /**
     * 分页查询总结
     *
     * @param query   分页参数
     * @param summary 查询条件
     * @return 分页结果
     */
    PageResult<WorkSummary> selectPage(PageQuery query, WorkSummary summary);

    /**
     * 总结详情（含 contentMd / contentHtml / sourceData）
     *
     * @param summaryId 总结ID
     * @return 总结
     */
    WorkSummary selectById(Long summaryId);

    /**
     * 删除总结（物理删除）
     *
     * @param summaryIds 总结ID集合
     * @return 影响行数
     */
    int deleteSummaries(List<Long> summaryIds);

    /**
     * 标记邮件已发送
     *
     * @param summaryId 总结ID
     * @param mailTo    收件人，逗号分隔
     * @return 影响行数
     */
    int markMailSent(Long summaryId, String mailTo);
}
