package com.aip.platform.knowledge.service;

import com.aip.platform.knowledge.entity.KnowledgeChunk;

import java.util.List;

/**
 * 知识检索服务：关键词粗召回 + 向量重排（无 embedding 时自动降级）
 */
public interface IKnowledgeSearchService {

    /**
     * 在单个知识库内检索
     *
     * @param baseId 知识库ID
     * @param query  查询词
     * @param topK   返回条数，为空用知识库默认配置
     */
    List<KnowledgeChunk> search(Long baseId, String query, Integer topK);

    /**
     * 在多个知识库内检索并合并（按分数排序）
     */
    List<KnowledgeChunk> searchMulti(List<Long> baseIds, String query, Integer topK);

    /**
     * 检索结果拼成可直接塞进提示词的上下文
     *
     * @param maxChars 最大字符数，超出则截断
     */
    String buildContext(List<Long> baseIds, String query, Integer topK, int maxChars);
}
