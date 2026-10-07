package com.aip.platform.knowledge.service;

import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.aip.platform.knowledge.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务：上传 → 解析 → 分片 → 入库
 */
public interface IKnowledgeDocumentService {

    List<KnowledgeDocument> selectByBaseId(Long baseId);

    KnowledgeDocument selectDetail(Long docId);

    /**
     * 上传并解析文档
     *
     * @param baseId 知识库ID
     * @param file   上传的文件
     * @return 解析后的文档记录（含分片数、字符数）
     */
    KnowledgeDocument upload(Long baseId, MultipartFile file);

    /** 重新解析（原文仍在磁盘上） */
    KnowledgeDocument reparse(Long docId);

    /** 停用 / 启用（停用的文档不参与检索） */
    int changeStatus(Long docId, Integer status);

    int deleteDocs(List<Long> docIds);

    /** 某文档的分片列表 */
    List<KnowledgeChunk> selectChunks(Long docId);

    /** 为知识库中缺少向量的分片补向量；返回成功生成的条数 */
    int buildEmbedding(Long baseId);

    /** 清空知识库向量（切换 embedding 模型后使用） */
    int clearEmbedding(Long baseId);
}
