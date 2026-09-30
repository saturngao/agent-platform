package com.aip.platform.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 画布定义：与前端 Vue Flow 的 {nodes:[],edges:[]} 结构一一对应
 *
 * <p>持久化时序列化为 wf_definition.graph_json，查询详情时再反序列化回对象返回前端。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphDef implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点列表 */
    private List<GraphNode> nodes = new ArrayList<>();

    /** 连线列表 */
    private List<GraphEdge> edges = new ArrayList<>();
}
