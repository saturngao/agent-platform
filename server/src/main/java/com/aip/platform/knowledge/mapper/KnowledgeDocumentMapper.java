package com.aip.platform.knowledge.mapper;

import com.aip.platform.knowledge.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库文档 Mapper
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /** 某知识库下的文档（含知识库名） */
    @Select("SELECT d.*, b.base_name FROM kb_document d "
            + " LEFT JOIN kb_base b ON b.base_id = d.base_id "
            + " WHERE d.base_id = #{baseId} AND d.del_flag = 0 ORDER BY d.doc_id DESC")
    List<KnowledgeDocument> selectByBaseId(@Param("baseId") Long baseId);

    @Update("UPDATE kb_document SET parse_status = #{status}, parse_msg = #{msg} WHERE doc_id = #{docId}")
    int updateParseStatus(@Param("docId") Long docId, @Param("status") Integer status, @Param("msg") String msg);

    @Update("UPDATE kb_document SET char_count = #{charCount}, chunk_count = #{chunkCount}, "
            + " parse_status = 2, parse_msg = #{msg} WHERE doc_id = #{docId}")
    int finishParse(@Param("docId") Long docId, @Param("charCount") Integer charCount,
                    @Param("chunkCount") Integer chunkCount, @Param("msg") String msg);
}
