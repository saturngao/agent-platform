package com.aip.platform.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 技能与业务对象的绑定关系
 *
 * <p>记录某个技能被挂到哪个智能体（AGENT）或工作流（WORKFLOW）上。
 * 用独立关联表而不是在业务表里加字段，是为了让一个技能能被多个对象复用。</p>
 */
@Data
@TableName("skill_binding")
public class SkillBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "bind_id", type = IdType.AUTO)
    private Long bindId;

    /** 技能ID */
    private Long skillId;

    /** 业务类型：AGENT / WORKFLOW */
    private String bizType;

    /** 业务对象ID：agent_id 或 def_id */
    private Long bizId;

    private String createBy;

    private java.time.LocalDateTime createTime;

    /** 业务类型常量，避免各处写字符串字面量 */
    public static final class BizType {
        /** 智能体 */
        public static final String AGENT = "AGENT";
        /** 工作流 */
        public static final String WORKFLOW = "WORKFLOW";

        private BizType() {
        }
    }
}
