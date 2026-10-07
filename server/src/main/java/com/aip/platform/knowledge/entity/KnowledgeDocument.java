package com.aip.platform.knowledge.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档
 *
 * <p>只保存元信息，原始文件落盘在知识库目录下，避免数据库膨胀。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KnowledgeDocument extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    private Long baseId;

    /** 原始文件名 */
    private String docName;

    /** 扩展名：pdf / docx / xlsx / csv / txt ... */
    private String fileType;

    /** 文件字节数 */
    private Long fileSize;

    /** 相对知识库根目录的存储路径 */
    private String storePath;

    /** 解析出的字符数 */
    private Integer charCount;

    /** 切出的分片数 */
    private Integer chunkCount;

    /** 解析状态：0待解析 1解析中 2成功 3失败 */
    private Integer parseStatus;

    /** 解析说明 / 失败原因 */
    private String parseMsg;

    /** 0启用 1停用（停用后不参与检索） */
    private Integer status;

    private Integer delFlag;

    // ---------- 非数据库字段 ----------
    /** 所属知识库名称 */
    @TableField(exist = false)
    private String baseName;

    /** 人类可读的文件大小 */
    @TableField(exist = false)
    private String fileSizeText;
}
