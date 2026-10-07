package com.aip.platform.knowledge.mapper;

import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库分片 Mapper
 *
 * <p>检索分两步：SQL 用 LIKE 做粗召回（候选集），
 * 打分与向量重排在内存中完成，避免把整表拉到应用里。</p>
 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 关键词粗召回：命中任意一个关键词即入选
     *
     * @param baseId 知识库ID
     * @param terms  查询词（已分词/清洗）
     * @param limit  候选上限
     */
    @Select({"<script>",
            "SELECT c.*, d.doc_name FROM kb_chunk c ",
            " JOIN kb_document d ON d.doc_id = c.doc_id AND d.del_flag = 0 AND d.status = 0 ",
            " WHERE c.base_id = #{baseId} ",
            "<if test='terms != null and terms.size() &gt; 0'>",
            "  AND (",
            "  <foreach collection='terms' item='t' separator=' OR '>",
            "    c.content LIKE CONCAT('%', #{t}, '%')",
            "  </foreach>",
            "  )",
            "</if>",
            " ORDER BY c.chunk_id ",
            " LIMIT #{limit}",
            "</script>"})
    List<KnowledgeChunk> recall(@Param("baseId") Long baseId, @Param("terms") List<String> terms, @Param("limit") int limit);

    /**
     * 无关键词时（或关键词无命中）取前 N 条作为兜底候选
     */
    @Select("SELECT c.*, d.doc_name FROM kb_chunk c "
            + " JOIN kb_document d ON d.doc_id = c.doc_id AND d.del_flag = 0 AND d.status = 0 "
            + " WHERE c.base_id = #{baseId} ORDER BY c.chunk_id LIMIT #{limit}")
    List<KnowledgeChunk> latest(@Param("baseId") Long baseId, @Param("limit") int limit);

    @Select("SELECT * FROM kb_chunk WHERE doc_id = #{docId} ORDER BY seq_no")
    List<KnowledgeChunk> selectByDocId(@Param("docId") Long docId);

    @Select("SELECT * FROM kb_chunk WHERE base_id = #{baseId} ORDER BY doc_id, seq_no LIMIT #{limit}")
    List<KnowledgeChunk> selectByBaseId(@Param("baseId") Long baseId, @Param("limit") int limit);

    /** 取该知识库已生成向量的分片（用于重排时判断是否需要补向量） */
    @Select("SELECT COUNT(1) FROM kb_chunk WHERE base_id = #{baseId} AND embedding IS NOT NULL AND embedding <> ''")
    int countWithEmbedding(@Param("baseId") Long baseId);

    @Delete("DELETE FROM kb_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM kb_chunk WHERE base_id = #{baseId}")
    int deleteByBaseId(@Param("baseId") Long baseId);
}
