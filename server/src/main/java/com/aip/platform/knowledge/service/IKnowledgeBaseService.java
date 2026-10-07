package com.aip.platform.knowledge.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.knowledge.entity.KnowledgeBase;

import java.util.List;
import java.util.Map;

/**
 * 知识库服务
 */
public interface IKnowledgeBaseService {

    PageResult<KnowledgeBase> selectPage(PageQuery query, KnowledgeBase params);

    KnowledgeBase selectDetail(Long baseId);

    /** 下拉选项：工作流节点 / 智能体绑定知识库用 */
    List<Map<String, Object>> selectOptions();

    int insertBase(KnowledgeBase base);

    int updateBase(KnowledgeBase base);

    int deleteBases(List<Long> baseIds);

    /** 重算文档数与分片数 */
    void recount(Long baseId);
}
