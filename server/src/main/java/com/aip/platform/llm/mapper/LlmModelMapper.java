package com.aip.platform.llm.mapper;

import com.aip.platform.llm.entity.LlmModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 模型实例 Mapper
 */
@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {

    @Select("<script>"
            + "SELECT m.*, p.provider_name AS providerName, p.base_url AS providerBaseUrl "
            + "FROM llm_model m LEFT JOIN llm_provider p ON m.provider_id = p.provider_id "
            + "<where>"
            + "  <if test='q.modelName != null and q.modelName != \"\"'>"
            + "    AND (m.model_name LIKE concat('%', #{q.modelName}, '%') OR m.model_code LIKE concat('%', #{q.modelName}, '%'))"
            + "  </if>"
            + "  <if test='q.modelCode != null and q.modelCode != \"\"'>AND m.model_code = #{q.modelCode}</if>"
            + "  <if test='q.modelType != null and q.modelType != \"\"'>AND m.model_type = #{q.modelType}</if>"
            + "  <if test='q.status != null'>AND m.status = #{q.status}</if>"
            + "  <if test='q.providerId != null'>AND m.provider_id = #{q.providerId}</if>"
            + "</where> ORDER BY m.is_default DESC, m.model_id DESC"
            + "</script>")
    List<LlmModel> selectModelList(@org.apache.ibatis.annotations.Param("q") LlmModel q);

    @Select("SELECT model_id AS value, CONCAT(model_name,' (',model_code,')') AS label "
            + "FROM llm_model WHERE model_type = 'chat' AND status = 0 ORDER BY is_default DESC, model_id")
    List<Map<String, Object>> selectChatOptions();
}
