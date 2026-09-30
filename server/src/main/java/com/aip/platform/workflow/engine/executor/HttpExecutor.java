package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP 请求节点执行器
 *
 * <p>data 约定：method（默认 GET）、url（支持变量渲染）、headers（Map）、body（支持变量渲染）、outputVar。</p>
 * <p>响应体优先按 JSON 解析，解析失败则按纯文本写入上下文。</p>
 */
@Slf4j
@Component
public class HttpExecutor implements NodeExecutor {

    private static final int TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return NodeType.HTTP.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String method = NodeData.str(data.get("method")).toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = "GET";
        }
        String url = context.render(NodeData.str(data.get("url")));
        if (StrUtil.isBlank(url)) {
            throw new BusinessException("HTTP节点[" + node.getName() + "]未配置 url");
        }
        String body = context.render(NodeData.str(data.get("body")));

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));
            Object headers = data.get("headers");
            if (headers instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    builder.header(String.valueOf(entry.getKey()), NodeData.str(entry.getValue()));
                }
            }
            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        body == null ? "" : body, StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BusinessException("HTTP节点[" + node.getName() + "]请求失败：HTTP "
                        + response.statusCode() + " " + truncate(response.body()));
            }
            context.setNodeOutput(node, parseBody(response.body()));
            log.debug("HTTP节点[{}]执行完成，status={}", node.getName(), response.statusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("HTTP节点[" + node.getName() + "]调用异常：" + e.getMessage());
        }
    }

    /** 错误信息截断，避免超长响应体写入 error_msg */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    /** 响应体解析：能解析成 JSON 就返回对象，否则返回原始文本 */
    private Object parseBody(String body) {
        if (StrUtil.isBlank(body)) {
            return "";
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }
}
