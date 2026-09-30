package com.aip.platform.llm.controller;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.llm.client.LlmClient;
import com.aip.platform.llm.client.LlmMessage;
import com.aip.platform.llm.client.LlmResponse;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.llm.service.ILlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型实例配置接口
 */
@Slf4j
@RestController
@RequestMapping("/api/llm/model")
@RequiredArgsConstructor
public class LlmModelController {

    private final ILlmModelService modelService;
    private final LlmClient llmClient;

    /**
     * 模型列表（分页）
     */
    @GetMapping({"", "/list"})
    public R<PageResult<LlmModel>> list(LlmModel params, PageQuery query) {
        return R.ok(modelService.selectPage(query, params));
    }

    /**
     * 模型详情
     */
    @GetMapping("/{modelId}")
    public R<LlmModel> detail(@PathVariable("modelId") Long modelId) {
        LlmModel model = modelService.getById(modelId);
        if (model == null) {
            return R.fail("模型不存在");
        }
        return R.ok(model);
    }

    /**
     * 新增模型
     */
    @PreAuthorize("@permissionChecker.has('llm:model:add')")
    @PostMapping({"", "/add"})
    public R<Map<String, Object>> add(@RequestBody LlmModel model) {
        modelService.insertModel(model);
        Map<String, Object> data = new HashMap<>(2);
        data.put("modelId", model.getModelId());
        return R.ok("新增成功", data);
    }

    /**
     * 修改模型
     */
    @PreAuthorize("@permissionChecker.has('llm:model:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody LlmModel model) {
        modelService.updateModel(model);
        return R.ok();
    }

    /**
     * 删除模型（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('llm:model:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        modelService.deleteModels(ids);
        return R.ok();
    }

    /**
     * 设为默认模型
     */
    @PreAuthorize("@permissionChecker.has('llm:model:edit')")
    @PutMapping("/default")
    public R<Void> setDefault(@RequestBody Map<String, Object> body) {
        Object id = body.get("modelId");
        if (id == null) {
            return R.fail("modelId 不能为空");
        }
        modelService.setDefault(Long.valueOf(String.valueOf(id)));
        return R.ok();
    }

    /**
     * 查询默认模型
     */
    @GetMapping("/default")
    public R<LlmModel> getDefault() {
        return R.ok(modelService.selectDefaultModel());
    }

    /**
     * 连通性测试：真的发一条请求给大模型
     */
    @PostMapping("/test")
    public R<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("modelId");
        String prompt = String.valueOf(body.getOrDefault("prompt", "你好，请用一句话介绍你自己。"));
        long start = System.currentTimeMillis();
        try {
            LlmModel model;
            if (idObj != null && !String.valueOf(idObj).isEmpty() && !"null".equals(String.valueOf(idObj))) {
                model = llmClient.getOrLoadModel(Long.valueOf(String.valueOf(idObj)));
            } else {
                model = llmClient.getOrLoadModel(null);
            }
            List<LlmMessage> messages = new ArrayList<>();
            messages.add(LlmMessage.user(prompt));
            LlmResponse response = llmClient.chat(model, messages, null, 256, "test", null);

            Map<String, Object> data = new HashMap<>(8);
            data.put("success", response.isSuccess());
            data.put("content", response.getContent());
            data.put("modelName", model.getModelName());
            data.put("endpoint", model.getEndpoint());
            data.put("tokens", response.getTotalTokens());
            data.put("costMs", response.getCostMs());
            data.put("errorMsg", response.getErrorMsg());

            // 回写测试结果
            LlmModel update = new LlmModel();
            update.setModelId(model.getModelId());
            update.setLastTestAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            update.setLastTestMsg(response.isSuccess()
                    ? "连通正常，耗时 " + response.getCostMs() + "ms"
                    : "调用失败：" + response.getErrorMsg());
            modelService.updateModel(update);

            if (!response.isSuccess()) {
                return R.fail(500, "连通测试失败：" + response.getErrorMsg(), data);
            }
            return R.ok("连通正常", data);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>(4);
            data.put("success", false);
            data.put("costMs", (int) (System.currentTimeMillis() - start));
            data.put("errorMsg", e.getMessage());
            return R.fail(500, "连通测试失败：" + e.getMessage(), data);
        }
    }

    /**
     * 下拉选项：可用的对话模型
     */
    @GetMapping("/optionselect")
    public R<List<Map<String, Object>>> optionselect() {
        return R.ok(modelService.selectChatOptions());
    }
}
