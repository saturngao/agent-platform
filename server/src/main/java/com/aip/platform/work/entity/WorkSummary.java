package com.aip.platform.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 工作总结
 *
 * <p>work_summary 表没有 del_flag（不做逻辑删除）也没有 remark 列，
 * 因此既不继承 BaseEntity 也不加 {@code @TableLogic}。</p>
 */
@Data
@TableName("work_summary")
public class WorkSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "summary_id", type = IdType.AUTO)
    private Long summaryId;

    private String summaryTitle;

    /** USER / DEPT / TEAM */
    private String scopeType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate beginDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 统计对象用户ID，逗号分隔 */
    private String targetIds;

    private Long modelId;

    private String modelName;

    /** 本次使用的提示词模板（不含原始数据，便于复盘） */
    private String promptTpl;

    /** 原始数据快照 JSON：{"text":"...","logIds":[...]} */
    private String sourceData;

    /** Markdown 正文 */
    private String contentMd;

    /** 转成的 HTML（邮件推送用） */
    private String contentHtml;

    private Integer logCount;

    private Integer tokens;

    private Integer costMs;

    /** GENERATING / SUCCESS / FAILED */
    private String status;

    private String errorMsg;

    /** 是否已发送邮件：0 否 1 是 */
    private Integer mailSent;

    /** 收件人，逗号分隔 */
    private String mailTo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime mailSentAt;

    /** 创建人ID */
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ==================== 非数据库字段 ====================

    /** 前端别名：memberIds -> targetIds */
    @TableField(exist = false)
    private List<Long> memberIds;

    /** 前端别名：summaryType -> scopeType（USER / DEPT / TEAM） */
    @TableField(exist = false)
    private String summaryType;

    /** 前端别名：title -> summaryTitle */
    @TableField(exist = false)
    private String title;

    /** 前端别名：content -> contentMd（生成结果直接返回给前端渲染） */
    @TableField(exist = false)
    private String content;

    /** 统计部门（scopeType = DEPT 时使用，不落库） */
    @TableField(exist = false)
    private Long deptId;
}
