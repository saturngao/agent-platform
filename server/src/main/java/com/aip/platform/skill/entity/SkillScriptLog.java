package com.aip.platform.skill.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能脚本执行记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_script_log")
public class SkillScriptLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 脚本ID（在线调试执行时为空） */
    private Long scriptId;

    /** 所属技能ID */
    private Long skillId;

    /** 脚本编码快照 */
    private String scriptCode;

    /** 触发来源：MANUAL 手工 / AGENT 对话 */
    private String bizType;

    /** 触发对象ID（智能体ID等） */
    private String bizId;

    /**
     * 入参 JSON。
     * 注意不能叫 params：基类 BaseEntity 已有 Map 类型的 params（接收前端扩展查询条件），
     * 同名会与方法签名冲突导致编译失败。
     */
    private String inputParams;

    /** 进程退出码 */
    private Integer exitCode;

    /** 标准输出 */
    private String outputText;

    /** 错误输出 */
    private String errorText;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 执行结果：0 成功 1 失败 */
    private Integer status;
}
