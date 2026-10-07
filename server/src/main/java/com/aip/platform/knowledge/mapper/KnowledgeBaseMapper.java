package com.aip.platform.knowledge.mapper;

import com.aip.platform.knowledge.entity.KnowledgeBase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库 Mapper
 */
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /** 按编码查询（排除已删除） */
    @Select("SELECT * FROM kb_base WHERE base_code = #{code} AND del_flag = 0 LIMIT 1")
    KnowledgeBase selectByCode(@Param("code") String code);

    /** 重算文档/分片数量 */
    @Update("UPDATE kb_base b SET "
            + " doc_count = (SELECT COUNT(1) FROM kb_document d WHERE d.base_id = b.base_id AND d.del_flag = 0), "
            + " chunk_count = (SELECT COUNT(1) FROM kb_chunk c WHERE c.base_id = b.base_id) "
            + " WHERE b.base_id = #{baseId}")
    int recount(@Param("baseId") Long baseId);
}
