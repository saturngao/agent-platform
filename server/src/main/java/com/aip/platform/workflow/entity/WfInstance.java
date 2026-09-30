package com.aip.platform.workflow.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流运行实例
 *
 * <p>对应表 wf_instance。该表没有 del_flag，删除为物理删除，因此不加 @TableLogic。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_instance")
public class WfInstance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运行状态 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_STOPPED = "STOPPED";

    /** 触发方式 */
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_API = "API";
    public static final String TRIGGER_SCHEDULE = "SCHEDULE";

    @TableId(value = "instance_id", type = IdType.AUTO)
    private Long instanceId;

    private Long defId;

    /** 冗余流程编码 */
    private String defCode;

    /** 业务单号（外部关联） */
    private String bizNo;

    /** 入参 JSON */
    private String inputData;

    /** 出参 JSON */
    private String outputData;

    /** 运行期变量快照 JSON */
    private String variables;

    /** RUNNING/SUCCESS/FAILED/STOPPED */
    private String status;

    private String errorMsg;

    /** MANUAL/API/SCHEDULE */
    private String triggerType;

    private Integer costMs;

    private Integer tokenTotal;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long userId;

    private String username;

    // ---------------- 非数据库字段 ----------------

    /** wf_instance 无 remark 列，屏蔽基类字段避免写入 SQL */
    @TableField(exist = false)
    private String remark;

    /** 列表展示：流程名称（关联 wf_definition.def_name） */
    @TableField(exist = false)
    private String defName;

    /** 详情展示：节点执行明细 */
    @TableField(exist = false)
    private List<WfNodeRun> nodeRuns;
}
