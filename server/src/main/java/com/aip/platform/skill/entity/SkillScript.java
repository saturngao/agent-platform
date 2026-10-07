package com.aip.platform.skill.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能脚本实体
 *
 * <p>一个技能可以挂载多个可执行脚本。脚本编码在技能内唯一，
 * 大模型在对话中通过脚本编码调用对应脚本。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_script")
public class SkillScript extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "script_id", type = IdType.AUTO)
    private Long scriptId;

    /** 所属技能ID */
    private Long skillId;

    /** 脚本编码（技能内唯一，模型调用时使用） */
    private String scriptCode;

    /** 脚本名称 */
    private String scriptName;

    /** 用途说明：给大模型看，决定什么时候该调用它 */
    private String description;

    /** 参数说明，建议写 JSON 样例 */
    private String paramDesc;

    /** 脚本语言，当前仅支持 python */
    private String language;

    /** 脚本源码 */
    private String content;

    /** 超时秒数 */
    private Integer timeoutSec;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 删除标记：0 存在 2 删除 */
    @TableLogic(value = "0", delval = "2")
    private Integer delFlag;

    /** 非表字段：所属技能名称，列表展示用 */
    @TableField(exist = false)
    private String skillName;

    /** 非表字段：所属技能编码，便于日志与提示词定位 */
    @TableField(exist = false)
    private String skillCode;
}
