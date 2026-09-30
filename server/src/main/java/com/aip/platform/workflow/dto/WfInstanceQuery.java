package com.aip.platform.workflow.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 运行实例查询条件
 *
 * <p>时间区间用字符串接收（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss），
 * 单独建一个查询对象是为了避免与 WfInstance 实体中已存在的 end_time 列字段重名。</p>
 */
@Data
public class WfInstanceQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义ID */
    private Long defId;

    /** 流程名称（模糊匹配 wf_definition.def_name） */
    private String defName;

    /** 运行状态：RUNNING/SUCCESS/FAILED/STOPPED */
    private String status;

    /** 开始时间起 */
    private String beginTime;

    /** 开始时间止 */
    private String endTime;
}
