package com.aip.platform.knowledge.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 知识库
 *
 * <p>检索的范围单位：一个知识库下挂若干文档，文档解析后切成若干分片。
 * 工作流知识节点与智能体都通过 baseId 引用知识库。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_base")
public class KnowledgeBase extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "base_id", type = IdType.AUTO)
    private Long baseId;

    /** 知识库编码（唯一），同时作为磁盘目录名 */
    private String baseCode;

    private String baseName;

    private String description;

    /** embedding 模型ID，为空表示纯关键词检索 */
    private Long embedModelId;

    /** 分片大小（字符） */
    private Integer chunkSize;

    /** 分片重叠（字符） */
    private Integer chunkOverlap;

    /** 默认召回条数 */
    private Integer topK;

    /** 召回最低分数 */
    private BigDecimal scoreThreshold;

    /** 文档数（冗余） */
    private Integer docCount;

    /** 分片数（冗余） */
    private Integer chunkCount;

    /** 状态 0正常 1停用 */
    private Integer status;

    private Integer delFlag;

    private Long deptId;

    // ---------- 非数据库字段 ----------
    /** embedding 模型名称（列表展示） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String embedModelName;

    /** 是否可用向量检索（有模型且已生成向量） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean vectorReady;
}
