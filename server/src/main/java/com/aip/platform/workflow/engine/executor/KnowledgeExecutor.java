package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.knowledge.service.IKnowledgeSearchService;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库节点执行器
 *
 * <p>两种用法，可同时配置：</p>
 * <ol>
 *   <li><b>文档检索（推荐）</b>：指定知识库（baseIds）+ 查询词（query），
 *       从用户上传的文档中召回相关片段注入 outputVar；</li>
 *   <li><b>静态知识（兜底）</b>：直接把 content 渲染后的文本注入，行为与 MVP 一致。</li>
 * </ol>
 *
 * <p>检索不到内容时自动回退到静态 content，保证已有流程不会突然拿不到上下文。</p>
 *
 * <p>data 约定：baseIds（数组或逗号串）/ baseId、query、topK、content、outputVar。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeExecutor implements NodeExecutor {

    /** 注入到提示词的知识最大字符数 */
    private static final int MAX_CHARS = 6000;

    private final IKnowledgeSearchService searchService;

    @Override
    public String getType() {
        return NodeType.KNOWLEDGE.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String staticContent = context.render(NodeData.str(data.get("content")));

        List<Long> baseIds = parseIds(data.get("baseIds"), data.get("baseId"));
        String query = context.render(NodeData.str(data.get("query")));
        if (StrUtil.isBlank(query)) {
            // 未单独配置查询词时，用流程入参里最常见的原始文本兜底
            query = context.render("{{rawText}}");
        }
        // 变量没渲染出来（上下文里没有该变量）时按空处理，避免拿 {{rawText}} 当检索词
        if (StrUtil.isNotBlank(query) && query.contains("{{")) {
            query = "";
        }

        String injected = null;
        if (!baseIds.isEmpty() && StrUtil.isNotBlank(query)) {
            Integer topK = toInt(data.get("topK"));
            try {
                injected = searchService.buildContext(baseIds, query, topK, MAX_CHARS);
            } catch (Exception e) {
                log.warn("知识库节点[{}]检索失败，回退静态知识：{}", node.getName(), e.getMessage());
            }
        }

        if (StrUtil.isBlank(injected)) {
            injected = staticContent;
        }
        context.setNodeOutput(node, injected == null ? "" : injected);
        log.debug("知识库节点[{}]注入片段长度={}（检索={}）", node.getName(),
                injected == null ? 0 : injected.length(), !baseIds.isEmpty());
    }

    private List<Long> parseIds(Object baseIds, Object single) {
        List<Long> ids = new ArrayList<>();
        collect(ids, baseIds);
        if (ids.isEmpty()) {
            collect(ids, single);
        }
        return ids;
    }

    private void collect(List<Long> ids, Object v) {
        if (v == null) {
            return;
        }
        if (v instanceof Number n) {
            ids.add(n.longValue());
            return;
        }
        if (v instanceof Iterable<?> it) {
            for (Object o : it) {
                collect(ids, o);
            }
            return;
        }
        String s = String.valueOf(v).replaceAll("[\\[\\]\\\"']", "");
        for (String part : s.split("[,，\\s]+")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(t);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法项
            }
        }
    }

    private Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
