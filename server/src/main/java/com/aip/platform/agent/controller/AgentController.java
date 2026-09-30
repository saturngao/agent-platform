package com.aip.platform.agent.controller;

import com.aip.platform.agent.dto.AgentChatRequest;
import com.aip.platform.agent.dto.AgentReplyDTO;
import com.aip.platform.agent.entity.AgentConversation;
import com.aip.platform.agent.entity.AgentDefinition;
import com.aip.platform.agent.entity.AgentMessage;
import com.aip.platform.agent.service.IAgentService;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能体模块接口
 *
 * <p>路径与前端 web/src/api/agent.js 保持一致（前端 axios baseURL = /api）；
 * 每个接口都做了 REST 双映射，兼容 /agent 与 /agent/add 两种写法。</p>
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final IAgentService agentService;

    // ============================ 智能体 ============================

    /**
     * 智能体列表（分页）
     */
    @GetMapping({"", "/list"})
    public R<PageResult<AgentDefinition>> list(AgentDefinition query, PageQuery pageQuery) {
        return R.ok(agentService.selectPage(pageQuery, query));
    }

    /**
     * 智能体详情
     */
    @GetMapping("/{agentId}")
    public R<AgentDefinition> detail(@PathVariable("agentId") Long agentId) {
        return R.ok(agentService.selectDetail(agentId));
    }

    /**
     * 新增智能体
     */
    @PreAuthorize("@permissionChecker.has('agent:def:add')")
    @PostMapping({"", "/add"})
    public R<Void> add(@RequestBody AgentDefinition body) {
        agentService.insertAgent(body);
        return R.ok();
    }

    /**
     * 修改智能体
     */
    @PreAuthorize("@permissionChecker.has('agent:def:edit')")
    @PutMapping({"", "/edit"})
    public R<Void> edit(@RequestBody AgentDefinition body) {
        agentService.updateAgent(body);
        return R.ok();
    }

    /**
     * 修改智能体状态：0草稿 1发布 2停用
     */
    @PreAuthorize("@permissionChecker.has('agent:def:edit')")
    @PutMapping("/status/{agentId}")
    public R<Void> changeStatus(@PathVariable("agentId") Long agentId,
                                @RequestParam("status") Integer status) {
        agentService.changeStatus(agentId, status);
        return R.ok();
    }

    /**
     * 发布智能体
     */
    @PreAuthorize("@permissionChecker.has('agent:def:publish')")
    @PutMapping("/publish/{agentId}")
    public R<Void> publish(@PathVariable("agentId") Long agentId) {
        agentService.publish(agentId);
        return R.ok();
    }

    /**
     * 批量删除智能体（ids 逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('agent:def:remove')")
    @DeleteMapping({"/{ids}", "/remove/{ids}"})
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        agentService.deleteAgents(ids);
        return R.ok();
    }

    // ============================ 会话 ============================

    /**
     * 会话列表（分页），query {agentId,pageNum,pageSize}
     */
    @GetMapping("/conversation")
    public R<PageResult<AgentConversation>> conversations(AgentConversation query, PageQuery pageQuery) {
        return R.ok(agentService.selectConversations(pageQuery, query));
    }

    /**
     * 批量删除会话（ids 逗号分隔，级联删除消息）
     */
    @PreAuthorize("@permissionChecker.has('agent:conv:remove')")
    @DeleteMapping("/conversation/{ids}")
    public R<Void> removeConversations(@PathVariable("ids") List<Long> ids) {
        agentService.deleteConversations(ids);
        return R.ok();
    }

    // ============================ 消息 ============================

    /**
     * 消息列表（分页），query {conversationId,pageNum,pageSize}
     */
    @GetMapping("/message")
    public R<PageResult<AgentMessage>> messages(AgentMessage query, PageQuery pageQuery) {
        return R.ok(agentService.selectMessages(pageQuery, query));
    }

    /**
     * 发送消息（同步）；conversationId 为空时自动新建会话
     */
    @PostMapping("/message/send")
    public R<AgentReplyDTO> send(@RequestBody AgentChatRequest body) {
        if (body == null) {
            throw new BusinessException("请求参数不能为空");
        }
        return R.ok(agentService.chat(body.getAgentId(), body.getConversationId(), body.getContent()));
    }

    /**
     * 发送消息（流式 SSE）：每个片段一个 event，末尾发送 [DONE]
     */
    @PostMapping(value = "/message/sendStream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> sendStream(@RequestBody AgentChatRequest body) {
        if (body == null) {
            throw new BusinessException("请求参数不能为空");
        }
        Flux<String> chunks = agentService.chatStream(body.getAgentId(), body.getConversationId(), body.getContent());
        return chunks
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                // 出错时不要直接让流异常终止：响应头一旦提交，浏览器会一直挂着等不到结束，
                // 前端就会永远卡在"正在思考"。这里把错误以 [ERROR] 文本发下去并正常结束流。
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? "智能体调用失败" : e.getMessage();
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("[ERROR]" + msg)
                            .build());
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()))
                // 兜底：任何异常都转成一条错误事件，保证流一定正常结束
                .onErrorReturn(ServerSentEvent.<String>builder()
                        .data("[ERROR]智能体调用失败，请检查模型配置（API Key 是否填写正确）")
                        .build());
    }
}
