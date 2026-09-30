package com.aip.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流节点运行记录
 *
 * <p>对应表 wf_node_run。该表只有 instance_id 等运行字段，
 * 没有 create_by / remark 等审计列，因此不继承 BaseEntity。</p>
 */
@Data
@TableName("wf_node_run")
public class WfNodeRun implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点运行状态 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableId(value = "run_id", type = IdType.AUTO)
    private Long runId;

    private Long instanceId;

    /** 画布节点ID */
    private String nodeId;

    private String nodeName;

    /** start/llm/code/condition/http/knowledge/agent/end */
    private String nodeType;

    /** 节点入参 */
    private String inputData;

    /** 节点出参 */
    private String outputData;

    /** 条件节点实际走向的分支 */
    private String branchTaken;

    /** RUNNING/SUCCESS/FAILED/SKIPPED */
    private String status;

    private String errorMsg;

    /** 使用的模型ID */
    private Long modelId;

    private Integer tokens;

    private Integer costMs;

    /** 执行顺序 */
    private Integer seqNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
