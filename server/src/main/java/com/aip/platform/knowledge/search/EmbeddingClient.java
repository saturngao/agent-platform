package com.aip.platform.knowledge.search;

import com.aip.platform.common.BusinessException;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.llm.mapper.LlmModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量化客户端（OpenAI 兼容 /embeddings 协议）
 *
 * <p>知识库的向量检索是「可选增强」：没有配置 embedding 模型时自动降级为关键词检索，
 * 因此这里的异常一律向上抛出，由检索服务捕获后降级，不影响主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final int TIMEOUT_MS = 30_000;
    /** 单次批量文本数上限，防止请求体过大 */
    private static final int BATCH = 16;

    private final LlmModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 取可用的 embedding 模型：优先指定ID，其次任一启用的 embedding 模型 */
    public LlmModel resolveModel(Long modelId) {
        if (modelId != null && modelId > 0) {
            LlmModel m = modelMapper.selectById(modelId);
            if (m != null && m.getStatus() != null && m.getStatus() == 0) {
                return m;
            }
        }
        List<LlmModel> list = modelMapper.selectList(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getModelType, "embedding")
                .eq(LlmModel::getStatus, 0)
                .orderByDesc(LlmModel::getIsDefault)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 单条向量化 */
    public float[] embed(Long modelId, String text) {
        List<float[]> res = embedBatch(modelId, List.of(text == null ? "" : text));
        return res.isEmpty() ? null : res.get(0);
    }

    /** 批量向量化（自动分批） */
    public List<float[]> embedBatch(Long modelId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        LlmModel model = resolveModel(modelId);
        if (model == null) {
            throw new BusinessException("未配置可用的 embedding 模型");
        }
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH) {
            List<String> part = texts.subList(i, Math.min(i + BATCH, texts.size()));
            all.addAll(call(model, part));
        }
        return all;
    }

    private List<float[]> call(LlmModel model, List<String> inputs) {
        String endpoint = resolveEndpoint(model);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelIdName(model));
        ArrayNode arr = body.putArray("input");
        inputs.forEach(arr::add);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + (model.getApiKey() == null ? "" : model.getApiKey()))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException("向量接口返回 " + resp.statusCode() + "：" + clip(resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new BusinessException("向量接口未返回 data：" + clip(resp.body()));
            }
            List<float[]> out = new ArrayList<>(data.size());
            for (JsonNode node : data) {
                JsonNode emb = node.get("embedding");
                if (emb == null || !emb.isArray()) {
                    continue;
                }
                float[] v = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    v[i] = (float) emb.get(i).asDouble();
                }
                out.add(v);
            }
            if (out.size() != inputs.size()) {
                throw new BusinessException("向量数量与输入不一致：" + out.size() + "/" + inputs.size());
            }
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调用向量模型失败：" + e.getMessage());
        }
    }

    private String resolveEndpoint(LlmModel model) {
        String endpoint = model.getEndpoint() == null ? "" : model.getEndpoint().trim();
        if (endpoint.isEmpty()) {
            throw new BusinessException("embedding 模型[" + model.getModelName() + "]未配置 endpoint");
        }
        String url = endpoint.replaceAll("/+$", "");
        if (url.endsWith(EMBEDDINGS_PATH)) {
            return url;
        }
        if (url.endsWith("/chat/completions")) {
            return url.substring(0, url.length() - "/chat/completions".length()) + EMBEDDINGS_PATH;
        }
        return url + EMBEDDINGS_PATH;
    }

    private String modelIdName(LlmModel model) {
        return model.getModelNameId() != null && !model.getModelNameId().isBlank()
                ? model.getModelNameId() : model.getModelCode();
    }

    private String clip(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
