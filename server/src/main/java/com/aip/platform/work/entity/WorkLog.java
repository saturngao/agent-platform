package com.aip.platform.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日志
 *
 * <p>与 {@link WorkTask} 同理：work_log 表没有 remark 列，故不继承 BaseEntity，
 * 只声明表中真实存在的列，避免 MyBatis-Plus 生成不存在的列。</p>
 *
 * <p>前端字段别名：hours->hoursSpent、progress->progressSnap、logTitle->logTitle（同名）。</p>
 */
@Data
@TableName("work_log")
public class WorkLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 关联任务，可为空（非任务类工作） */
    private Long taskId;

    private String logTitle;

    /** 日志正文，必填 */
    private String content;

    /** 工作日期，格式 yyyy-MM-dd */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate workDate;

    /** 投入工时（小时） */
    private BigDecimal hoursSpent;

    /** 进度快照 0~100 */
    private Integer progressSnap;

    /** DAILY / WEEKLY / MEETING / OTHER */
    private String logType;

    /** 当日成果 */
    private String result;

    /** 阻塞项 */
    private String blockers;

    /** 下一步计划 */
    private String planNext;

    private Long userId;

    /** 冗余：记录人账号 */
    private String username;

    private Long deptId;

    /** NORMAL / DELETED */
    private String status;

    /** 是否已被总结引用：0 否 1 是 */
    private Integer summarized;

    /** 0 正常 2 删除 */
    @TableLogic(value = "0", delval = "2")
    private Integer delFlag = 0;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ==================== 非数据库字段 ====================

    /** 关联任务标题（左连 work_task 取得） */
    @TableField(exist = false)
    private String taskTitle;

    /** 部门名称（左连 sys_dept 取得） */
    @TableField(exist = false)
    private String deptName;

    /** 前端别名：hours -> hoursSpent */
    @TableField(exist = false)
    private BigDecimal hours;

    /** 前端别名：progress -> progressSnap */
    @TableField(exist = false)
    private Integer progress;

    /**
     * 展示用姓名，优先取 nickname。
     * 注意：字段刻意命名为 displayName 而非 userName —— Lombok 会把 userName 与
     * 真实字段 username 视为同名访问器而跳过 getter/setter 生成。
     */
    @TableField(exist = false)
    private String displayName;

    /** 查询条件：正文/标题/成果模糊匹配 */
    @TableField(exist = false)
    private String keyword;

    /** 查询条件：工作日期起始（yyyy-MM-dd） */
    @TableField(exist = false)
    private String beginTime;

    /** 查询条件：工作日期结束（yyyy-MM-dd） */
    @TableField(exist = false)
    private String endTime;
}
