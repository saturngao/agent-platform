package com.aip.platform.skill.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 技能表实体
 *
 * <p>一个技能本质是一段「教模型怎么做事」的 Markdown 说明书（类似 SKILL.md），
 * 可被智能体或工作流挂载，调用大模型时作为系统提示词的补充注入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill")
public class Skill extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "skill_id", type = IdType.AUTO)
    private Long skillId;

    /** 技能编码（全局唯一） */
    private String skillCode;

    /** 技能名称 */
    private String skillName;

    /** 分类：办公/研发/运维/数据/写作 */
    private String category;

    /** 图标 emoji */
    private String icon;

    /** 版本 */
    private String version;

    /** 作者或来源方 */
    private String author;

    /** 来源：OFFICIAL 官方 / IMPORT 导入 / CUSTOM 自建 */
    private String source;

    /** 一句话简介 */
    private String summary;

    /** 技能正文（SKILL.md 全文） */
    private String content;

    /** 标签，逗号分隔 */
    private String tags;

    /** 是否已在磁盘建立技能目录（SKILL.md + scripts/） */
    private Integer dirFlag;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 删除标记：0 存在 2 删除 */
    @TableLogic(value = "0", delval = "2")
    private Integer delFlag;

    /** 归属部门（用于数据权限） */
    private Long deptId;

    /** 非表字段：标签数组，方便前端回显 */
    @TableField(exist = false)
    private List<String> tagList;

    /** 非表字段：绑定的智能体数量（列表展示用） */
    @TableField(exist = false)
    private Integer agentCount;

    /** 非表字段：绑定的工作流数量（列表展示用） */
    @TableField(exist = false)
    private Integer workflowCount;

    /** 非表字段：技能目录中的文件数量，列表展示用 */
    @TableField(exist = false)
    private Integer fileCount;

    /** 非表字段：技能目录绝对路径，便于排查 */
    @TableField(exist = false)
    private String dirPath;
}
