package com.aip.platform.llm.client;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.Constants;
import com.aip.platform.llm.entity.LlmCallLog;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.llm.service.ILlmCallLogService;
import com.aip.platform.llm.service.ILlmModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一大模型客户端
 *
 * <p>支持任意 OpenAI 兼容协议（OpenAI / DeepSeek / 通义千问 / 智谱 / Ollama / 自定义网关），
 * 提供同步与流式两种调用方式，并自动记录调用日志。</p>
 *
 * <p>说明：不依赖 WebFlux 的 codec 推断，直接使用 JDK HttpClient，
 * 避免部分第三方网关 Content-Type 不规范导致的流式解析失败。</p>
 */
@Slf4j
@Component
public class LlmClient {

    private static final String CHAT_PATH = "/chat/completions";
    private static final int MAX_BODY_LOG = 4000;

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(POOL)
            .build();

    private final ObjectMapper objectMapper;
    private final ILlmModelService modelService;
    private final ILlmCallLogService callLogService;

    public LlmClient(ObjectMapper objectMapper,
                     ILlmModelService modelService,
                     ILlmCallLogService callLogService) {
        this.objectMapper = objectMapper;
        this.modelService = modelService;
        this.callLogService = callLogService;
    }

    // ============================ 对外便捷入口 ============================

    /** 按模型ID调用（系统提示词+用户提示词） */
    public LlmResponse chat(Long modelId, String systemPrompt, String userPrompt) {
        return chat(modelId, systemPrompt, userPrompt, null, null,
                Constants.BizType.TEST, null);
    }

    /** 按模型ID调用，可覆盖温度与最大输出 */
    public LlmResponse chat(Long modelId, String systemPrompt, String userPrompt,
                            Double temperature, Integer maxTokens, String bizType, String bizId) {
        LlmModel model = getOrLoadModel(modelId);
        List<LlmMessage> messages = buildMessages(model, systemPrompt, userPrompt);
        return chat(model, messages, temperature, maxTokens, bizType, bizId);
    }

    /** 多轮对话 */
    public LlmResponse chat(LlmModel model, List<LlmMessage> messages) {
        return chat(model, messages, null, null, Constants.BizType.TEST, null);
    }

    /** 多轮对话，可覆盖参数 */
    public LlmResponse chat(LlmModel model, List<LlmMessage> messages,
                            Double temperature, Integer maxTokens, String bizType, String bizId) {
        long start = System.currentTimeMillis();
        String endpoint;
        String requestJson;
        try {
            validateModel(model);
            endpoint = resolveEndpoint(model);
            requestJson = buildRequestBody(model, messages, temperature, maxTokens, false);
        } catch (Exception e) {
            recordLog(model, null, null, null, null, (int) (System.currentTimeMillis() - start),
                    1, truncate(e.getMessage()), bizType, bizId);
            return LlmResponse.fail(e.getMessage());
        }

        try {
            HttpRequest request = buildRequest(endpoint, model, requestJson, timeoutOf(model));
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int cost = (int) (System.currentTimeMillis() - start);

            if (response.statusCode() >= 400) {
                String err = "HTTP " + response.statusCode() + ": " + truncate(response.body());
                recordLog(model, requestJson, null, null, null, cost, 1, err, bizType, bizId);
                return LlmResponse.fail(err);
            }

            LlmResponse result = parseResponse(response.body());
            result.setCostMs(cost);
            recordLog(model, requestJson, result.getContent(),
                    result.getPromptTokens(), result.getCompletionTokens(), cost,
                    result.isSuccess() ? 0 : 1, result.getErrorMsg(), bizType, bizId);
            return result;

        } catch (Exception e) {
            int cost = (int) (System.currentTimeMillis() - start);
            String err = truncate(e.getMessage());
            recordLog(model, requestJson, null, null, null, cost, 1, err, bizType, bizId);
            log.error("调用大模型失败 model={}: {}", model.getModelName(), err);
            return LlmResponse.fail("调用大模型失败: " + err);
        }
    }

    // ============================ 流式调用 ============================

    /** 流式对话，返回增量文本片段流 */
    public Flux<String> stream(Long modelId, List<LlmMessage> messages, String bizType, String bizId) {
        LlmModel model = getOrLoadModel(modelId);
        return stream(model, messages, null, null, bizType, bizId);
    }

    public Flux<String> stream(LlmModel model, List<LlmMessage> messages,
                               Double temperature, Integer maxTokens, String bizType, String bizId) {
        return Flux.create((FluxSink<String> sink) -> {
            long start = System.currentTimeMillis();
            String requestJson = null;
            StringBuilder full = new StringBuilder();
            AtomicInteger chunks = new AtomicInteger();
            try {
                validateModel(model);
                String endpoint = resolveEndpoint(model);
                requestJson = buildRequestBody(model, messages, temperature, maxTokens, true);
                HttpRequest request = buildRequest(endpoint, model, requestJson, timeoutOf(model));

                HttpResponse<InputStream> response =
                        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 400) {
                    String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    sink.error(new BusinessException("HTTP " + response.statusCode() + ": " + truncate(err)));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!sink.isCancelled() && (line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        String delta = extractDelta(data);
                        if (delta != null && !delta.isEmpty()) {
                            full.append(delta);
                            chunks.incrementAndGet();
                            sink.next(delta);
                        }
                    }
                }
                int cost = (int) (System.currentTimeMillis() - start);
                recordLog(model, requestJson, full.toString(), null, null, cost, 0, null, bizType, bizId);
                sink.complete();
            } catch (Exception e) {
                int cost = (int) (System.currentTimeMillis() - start);
                recordLog(model, requestJson, full.length() == 0 ? null : full.toString(),
                        null, null, cost, 1, truncate(e.getMessage()), bizType, bizId);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ============================ 模型获取 ============================

    /** 获取模型，未指定ID时取默认模型 */
    public LlmModel getOrLoadModel(Long modelId) {
        if (modelId != null) {
            LlmModel model = modelService.getById(modelId);
            if (model == null) {
                throw new BusinessException("模型不存在，modelId=" + modelId);
            }
            validateModel(model);
            return model;
        }
        LlmModel model = modelService.selectDefaultModel();
        if (model == null) {
            throw new BusinessException("尚未配置可用的大模型，请先在【模型中心】创建并启用一个 chat 模型");
        }
        return model;
    }

    private void validateModel(LlmModel model) {
        if (model == null) {
            throw new BusinessException("模型配置为空");
        }
        if (model.getStatus() != null && model.getStatus() == 1) {
            throw new BusinessException("模型[" + model.getModelName() + "]已停用");
        }
    }

    // ============================ 内部实现 ============================

    private List<LlmMessage> buildMessages(LlmModel model, String systemPrompt, String userPrompt) {
        String sys = StrUtil.isNotBlank(systemPrompt)
                ? systemPrompt
                : StrUtil.trimToEmpty(model.getSystemPrompt());
        if (sys.isEmpty()) {
            return Collections.singletonList(LlmMessage.user(userPrompt));
        }
        return List.of(LlmMessage.system(sys), LlmMessage.user(userPrompt));
    }

    private String resolveEndpoint(LlmModel model) {
        String endpoint = StrUtil.trimToEmpty(model.getEndpoint());
        if (endpoint.isEmpty()) {
            throw new BusinessException("模型[" + model.getModelName() + "]未配置请求地址(endpoint)");
        }
        String normalized = endpoint.replaceAll("/+$", "");
        if (!normalized.endsWith(CHAT_PATH)) {
            normalized = normalized + CHAT_PATH;
        }
        return normalized;
    }

    private int timeoutOf(LlmModel model) {
        if (model.getTimeoutMs() != null && model.getTimeoutMs() > 0) {
            return model.getTimeoutMs();
        }
        return 120000;
    }

    private String buildRequestBody(LlmModel model, List<LlmMessage> messages,
                                    Double temperature, Integer maxTokens, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelIdName(model));
        ArrayNode arr = body.putArray("messages");
        // OpenAI 兼容协议要求：使用 response_format=json_object 时，提示词里必须出现 "json" 字样，
        // 否则上游会直接返回 400。这里检测一下再决定是否开启 JSON 模式。
        boolean mentionJson = false;
        for (LlmMessage m : messages) {
            ObjectNode node = arr.addObject();
            node.put("role", m.getRole());
            node.put("content", m.getContent() == null ? "" : m.getContent());
            if (!mentionJson && m.getContent() != null
                    && m.getContent().toLowerCase().contains("json")) {
                mentionJson = true;
            }
        }
        double temp = temperature != null ? temperature
                : (model.getTemperature() != null ? model.getTemperature() : 0.7);
        body.put("temperature", Math.max(0.0, Math.min(2.0, temp)));
        body.put("top_p", model.getTopP() != null ? model.getTopP().doubleValue() : 1.0);
        int max = maxTokens != null ? maxTokens
                : (model.getMaxTokens() != null ? model.getMaxTokens() : 2048);
        body.put("max_tokens", Math.max(1, max));
        body.put("stream", stream);
        // 仅当模型支持 JSON 输出且提示词中确实提到 json 时才开启，避免上游 400
        if (!stream && mentionJson && model.getSupportJson() != null && model.getSupportJson() == 1) {
            ObjectNode fmt = body.putObject("response_format");
            fmt.put("type", "json_object");
        }
        return body.toString();
    }

    private String modelIdName(LlmModel model) {
        return StrUtil.isNotBlank(model.getModelNameId()) ? model.getModelNameId() : model.getModelCode();
    }

    private HttpRequest buildRequest(String endpoint, LlmModel model, String json, int timeoutMs) {
        // 提前校验：没有 API Key 时不发请求，直接给出可操作的提示，
        // 避免只看到上游一句 "Please Provide key" 却不知道要去哪里配
        if (StrUtil.isBlank(model.getApiKey())) {
            throw new BusinessException("模型「" + model.getModelName() + "」未配置 API Key，"
                    + "请到【模型中心 → 模型配置】编辑该模型并填写 API Key 后重试");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (StrUtil.isNotBlank(model.getApiKey())) {
            builder.header("Authorization", "Bearer " + model.getApiKey());
        }
        return builder.build();
    }

    private LlmResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("error")) {
                JsonNode error = root.get("error");
                String msg = error.hasNonNull("message")
                        ? error.get("message").asText()
                        : error.asText();
                return LlmResponse.fail("模型返回错误: " + truncate(msg));
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return LlmResponse.fail("模型未返回任何内容: " + truncate(body));
            }
            JsonNode first = choices.get(0);
            JsonNode message = first.path("message");
            String content = message.hasNonNull("content") ? message.get("content").asText() : "";

            JsonNode usage = root.path("usage");
            LlmResponse resp = LlmResponse.ok(content);
            resp.setModelName(root.path("model").asText(null));
            if (usage.isObject()) {
                resp.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                resp.setCompletionTokens(usage.path("completion_tokens").asInt(0));
                resp.setTotalTokens(usage.path("total_tokens").asInt(
                        resp.getPromptTokens() + resp.getCompletionTokens()));
            }
            resp.setRawResponse(truncate(body));
            return resp;
        } catch (Exception e) {
            return LlmResponse.fail("解析模型响应失败: " + truncate(e.getMessage()) + " | " + truncate(body));
        }
    }

    /** 从 SSE 的 data 块中取出增量文本 */
    private String extractDelta(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.hasNonNull("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception ignored) {
            // 心跳包或非 JSON 行，忽略
        }
        return null;
    }

    private void recordLog(LlmModel model, String requestBody, String responseBody,
                           Integer promptTokens, Integer completionTokens, int cost,
                           int status, String errorMsg, String bizType, String bizId) {
        try {
            LlmCallLog entity = new LlmCallLog();
            entity.setBizType(bizType == null ? Constants.BizType.TEST : bizType);
            entity.setBizId(bizId);
            if (model != null) {
                entity.setModelId(model.getModelId());
                entity.setModelName(model.getModelName());
            }
            entity.setRequestBody(requestBody == null ? null : truncate(requestBody, MAX_BODY_LOG));
            entity.setResponseBody(responseBody == null ? null : truncate(responseBody, MAX_BODY_LOG));
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setTotalTokens(
                    (promptTokens != null ? promptTokens : 0) + (completionTokens != null ? completionTokens : 0));
            entity.setCostMs(cost);
            entity.setStatus(status);
            entity.setErrorMsg(errorMsg == null ? null : truncate(errorMsg, 1000));
            callLogService.recordAsync(entity);
        } catch (Exception e) {
            log.debug("记录调用日志异常: {}", e.getMessage());
        }
    }

    private String truncate(String text) {
        return truncate(text, 500);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
