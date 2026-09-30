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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作任务
 *
 * <p>说明：work_task 表没有 remark 列，也没有 params 列，因此这里<b>不继承 BaseEntity</b>，
 * 仅保留表中真实存在的审计字段（create_by / create_time / update_by / update_time），
 * 保证 MyBatis-Plus 生成的 SQL 不会出现不存在的列。审计字段由
 * {@code MybatisPlusConfig#metaObjectHandler} 自动填充。</p>
 *
 * <p>前端字段别名（均为非表字段，由 Service 归一化到真实列）：
 * taskName->taskTitle、content->taskContent、assignee->assigneeId、
 * startTime->startDate、endTime->dueDate、remark->追加到 taskContent 末尾。</p>
 */
@Data
@TableName("work_task")
public class WorkTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /** 任务标题（前端别名 taskName） */
    private String taskTitle;

    /** 任务内容（前端别名 content） */
    private String taskContent;

    /** 负责人ID（前端别名 assignee，可能是数字ID也可能是姓名） */
    private Long assigneeId;

    /** 负责人姓名（冗余字段，便于列表直接展示） */
    private String assigneeName;

    private Long deptId;

    /** 指派人ID（默认当前登录人） */
    private Long assignerId;

    /** 优先级：LOW / NORMAL / HIGH / URGENT */
    private String priority;

    /** 状态：TODO / DOING / REVIEW / DONE / CANCELLED */
    private String status;

    /** 进度百分比 0~100 */
    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishDate;

    private String tags;

    /** 父任务ID，顶级任务为 0 或 null */
    private Long parentId;

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

    /** 指派人姓名（展示用） */
    @TableField(exist = false)
    private String assignerName;

    /** 部门名称（左连 sys_dept 取得） */
    @TableField(exist = false)
    private String deptName;

    /** 前端别名：taskName -> taskTitle */
    @TableField(exist = false)
    private String taskName;

    /** 前端别名：content -> taskContent */
    @TableField(exist = false)
    private String content;

    /** 前端别名：startTime -> startDate，格式 yyyy-MM-dd */
    @TableField(exist = false)
    private String startTime;

    /** 前端别名：endTime -> dueDate（保存语义）；列表查询时作为 create_time 结束区间 */
    @TableField(exist = false)
    private String endTime;

    /** 该表无 remark 列，保存时追加到 task_content 末尾 */
    @TableField(exist = false)
    private String remark;

    /** 前端别名：assignee -> assigneeId，支持用户ID数字或姓名字符串 */
    @TableField(exist = false)
    private String assignee;

    /** 查询条件：创建时间起始（yyyy-MM-dd） */
    @TableField(exist = false)
    private String beginTime;
}
