package com.aip.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分片：检索的最小单位
 *
 * <p>{@code embedding} 以 JSON float 数组形式存放（MySQL 无原生向量类型），
 * 检索时先关键词粗召回，再在内存中对候选做余弦相似度重排。</p>
 */
@Data
@TableName("kb_chunk")
public class KnowledgeChunk {

    private static final long serialVersionUID = 1L;

    @TableId(value = "chunk_id", type = IdType.AUTO)
    private Long chunkId;

    private Long baseId;

    private Long docId;

    private Integer seqNo;

    /** 分片标题（首个非空行 / 工作表名等） */
    private String title;

    private String content;

    private Integer charCount;

    /** 向量：JSON float 数组 */
    private String embedding;

    /** 生成向量的模型标识 */
    private String embedModel;

    private LocalDateTime createTime;

    // ---------- 非数据库字段 ----------
    /** 检索得分（不入库） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Double score;

    /** 关键词得分（调试用） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Double keywordScore;

    /** 向量得分（调试用） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Double vectorScore;

    /** 来源文档名（检索结果展示） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String docName;
}
