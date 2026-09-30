package com.aip.platform.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 画布节点
 *
 * <p>data 为各类型节点的私有配置，约定如下：</p>
 * <ul>
 *   <li>start    ：outputs(输出变量名数组)、variables(默认变量 Map)</li>
 *   <li>llm      ：modelId、systemPrompt、userPrompt、temperature、maxTokens、outputVar</li>
 *   <li>agent    ：agentId(预留)、systemPrompt、userPrompt、temperature、maxTokens、outputVar</li>
 *   <li>knowledge：content(支持变量渲染)、outputVar</li>
 *   <li>code     ：transform(template/jsonPath/join)、template、source、jsonPath、separator、outputVar</li>
 *   <li>http     ：method、url、headers(Map)、body、outputVar</li>
 *   <li>condition：branches([{condition,target,label}])、defaultTarget</li>
 *   <li>end      ：outputTemplate</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 画布内唯一节点ID */
    private String id;

    /** 节点类型：start/llm/code/condition/http/agent/knowledge/end */
    private String type;

    /** 节点名称（展示用） */
    private String name;

    /** 坐标 {x,y} */
    private Map<String, Object> position;

    /** 节点私有配置 */
    private Map<String, Object> data = new HashMap<>();
}
