package com.aip.platform.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.agent.dto.AgentReplyDTO;
import com.aip.platform.agent.entity.AgentConversation;
import com.aip.platform.agent.entity.AgentDefinition;
import com.aip.platform.agent.entity.AgentMessage;
import com.aip.platform.agent.mapper.AgentConversationMapper;
import com.aip.platform.agent.mapper.AgentDefinitionMapper;
import com.aip.platform.agent.mapper.AgentMessageMapper;
import com.aip.platform.agent.service.IAgentService;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.Constants;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.llm.client.LlmClient;
import com.aip.platform.llm.client.LlmMessage;
import com.aip.platform.llm.client.LlmResponse;
import com.aip.platform.llm.entity.LlmModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.aip.platform.skill.entity.SkillScript;
import com.aip.platform.skill.script.ScriptResult;
import com.aip.platform.skill.service.ISkillScriptService;

/**
 * 智能体模块服务实现
 *
 * <p>说明：chat / chatStream 刻意不加 @Transactional —— 调用大模型失败时也要保证
 * 用户消息与失败记录已经落库（刷新页面可见），因此这两条链路的每一次写库都是独立提交。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements IAgentService {

    /** 上下文窗口保护：最多携带的历史消息条数（偶数） */
    private static final int HISTORY_LIMIT = 20;

    /** 智能体未配置系统提示词时的兜底提示词 */
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个乐于助人的 AI 助手";

    /** 自动建会话时标题的最大长度 */
    private static final int TITLE_MAX_LEN = 20;

    /** error_msg 落库最大长度 */
    private static final int ERROR_MSG_MAX_LEN = 1000;

    /** 模型输出的脚本调用块：```tool {json} ``` */
    private static final Pattern TOOL_BLOCK =
            Pattern.compile("```tool\\s*\\r?\\n(.*?)```", Pattern.DOTALL);

    /** 流式输出时把最终答案切成的片段长度 */
    private static final int STREAM_CHUNK_SIZE = 24;

    /** 脚本服务：对话中按模型指令执行 Python 脚本 */
    private final ISkillScriptService scriptService;

    /** 对话中允许的最大脚本调用轮数，防止模型反复调用 */
    @Value("${platform.skill.python.max-tool-rounds:3}")
    private int maxToolRounds;

    private final AgentDefinitionMapper agentMapper;
    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    /** 技能服务：用于把挂载的技能内容注入系统提示词 */
    private final com.aip.platform.skill.service.ISkillService skillService;

    // ============================ 智能体 CRUD ============================

    @Override
    public PageResult<AgentDefinition> selectPage(PageQuery query, AgentDefinition q) {
        IPage<AgentDefinition> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AgentDefinition> result = agentMapper.selectAgentPage(page, q == null ? new AgentDefinition() : q);
        List<AgentDefinition> rows = result.getRecords();
        if (rows != null) {
            for (AgentDefinition row : rows) {
                fillTools(row);
                fillSkills(row);
            }
        }
        return PageResult.of(rows, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public AgentDefinition selectDetail(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        AgentDefinition agent = agentMapper.selectDetail(agentId);
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        fillTools(agent);
        fillSkills(agent);
        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertAgent(AgentDefinition agent) {
        if (agent == null) {
            throw new BusinessException("参数不能为空");
        }
        if (StrUtil.isBlank(agent.getAgentName())) {
            throw new BusinessException("智能体名称不能为空");
        }
        normalize(agent);
        if (StrUtil.isBlank(agent.getAgentCode())) {
            agent.setAgentCode("agent_" + IdUtil.fastSimpleUUID().substring(0, 8));
        }
        checkCodeUnique(agent);
        if (agent.getStatus() == null) {
            agent.setStatus(AgentDefinition.STATUS_DRAFT);
        }
        if (agent.getDeptId() == null) {
            agent.setDeptId(SecurityUtils.getDeptId());
        }
        if (StrUtil.isBlank(agent.getToolsJson())) {
            agent.setToolsJson("[]");
        }
        int rows = agentMapper.insert(agent);
        // 保存挂载的技能
        saveSkillBinding(agent.getAgentId(), agent.getSkillIds());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateAgent(AgentDefinition agent) {
        if (agent == null || agent.getAgentId() == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        AgentDefinition exists = agentMapper.selectDetail(agent.getAgentId());
        if (exists == null) {
            throw new BusinessException("智能体不存在");
        }
        normalize(agent);
        if (StrUtil.isNotBlank(agent.getAgentCode())) {
            checkCodeUnique(agent);
        } else {
            // 未传编码时保留原值，避免被空值覆盖
            agent.setAgentCode(exists.getAgentCode());
            agent.setAgentKey(exists.getAgentCode());
        }
        int rows = agentMapper.updateById(agent);
        // 重建挂载的技能（传 null 表示不改动，传空数组表示解除全部）
        if (agent.getSkillIds() != null) {
            saveSkillBinding(agent.getAgentId(), agent.getSkillIds());
        }
        return rows;
    }

    /**
     * 保存智能体与技能的绑定关系
     */
    private void saveSkillBinding(Long agentId, List<Long> skillIds) {
        try {
            skillService.bindSkills(
                    com.aip.platform.skill.entity.SkillBinding.BizType.AGENT, agentId, skillIds);
        } catch (Exception e) {
            log.warn("保存智能体技能绑定失败 agentId={}: {}", agentId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAgents(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 级联清理会话与消息，避免留下孤儿数据
        for (Long agentId : ids) {
            List<Long> convIds = conversationMapper.selectConvIdsByAgentId(agentId);
            if (convIds != null && !convIds.isEmpty()) {
                messageMapper.deleteByConvIds(convIds);
                conversationMapper.deleteByIds(convIds);
            }
        }
        return agentMapper.deleteByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long agentId, Integer status) {
        if (agentId == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        if (status == null || status < AgentDefinition.STATUS_DRAFT || status > AgentDefinition.STATUS_DISABLED) {
            throw new BusinessException("状态值非法");
        }
        AgentDefinition agent = new AgentDefinition();
        agent.setAgentId(agentId);
        agent.setStatus(status);
        return agentMapper.updateById(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long agentId) {
        return changeStatus(agentId, AgentDefinition.STATUS_PUBLISHED);
    }

    // ============================ 对话 ============================

    @Override
    public AgentReplyDTO chat(Long agentId, Long conversationId, String content) {
        // 1. 预处理：校验智能体与会话、落库用户消息、组装上下文、加载模型
        ChatContext ctx = prepareContext(agentId, conversationId, content);

        // 2. 调用大模型（同步）。挂载了脚本时走工具循环：模型要求调用脚本就执行并把结果回灌
        long start = System.currentTimeMillis();
        LlmResponse response;
        try {
            response = callModelWithTools(ctx);
        } catch (Exception e) {
            log.error("智能体调用异常 agentId={}: {}", agentId, e.getMessage());
            saveFailedReply(ctx, e.getMessage(), start);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }

        // 3. 失败分支：记录失败消息后抛出业务异常，由全局异常处理返回
        if (response == null || !response.isSuccess()) {
            String err = response == null ? "大模型未返回任何内容" : response.getErrorMsg();
            saveFailedReply(ctx, err, start);
            throw new BusinessException("智能体调用失败: " + err);
        }

        // 4. 成功分支：落库回复并回写会话统计
        int tokens = response.getTotalTokens() == null ? 0 : response.getTotalTokens();
        return finishReply(ctx, response.getContent(), tokens, response.getCostMs(), response.getModelName());
    }

    @Override
    public Flux<String> chatStream(Long agentId, Long conversationId, String content) {
        ChatContext ctx = prepareContext(agentId, conversationId, content);
        long start = System.currentTimeMillis();

        // 智能体挂了可执行脚本时，必须先拿到完整回复才能判断是否需要调用脚本，
        // 因此改为「先跑工具循环，再把最终答案分片推流」，保证与非流式链路行为一致。
        if (hasCallableScripts(ctx)) {
            return Flux.defer(() -> {
                try {
                    LlmResponse response = callModelWithTools(ctx);
                    if (response == null || !response.isSuccess()) {
                        String err = response == null ? "大模型未返回任何内容" : response.getErrorMsg();
                        saveFailedReply(ctx, err, start);
                        return Flux.error(new BusinessException("智能体调用失败: " + err));
                    }
                    int tokens = response.getTotalTokens() == null ? 0 : response.getTotalTokens();
                    finishReply(ctx, response.getContent(), tokens, response.getCostMs(), response.getModelName());
                    return Flux.fromIterable(splitForStream(response.getContent()));
                } catch (Exception e) {
                    log.error("智能体流式调用异常 agentId={}: {}", agentId, e.getMessage());
                    saveFailedReply(ctx, e.getMessage(), start);
                    return Flux.error(new BusinessException("智能体调用失败: " + e.getMessage()));
                }
            }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
        }

        // reactor 流里需要跨线程累积全文，故使用 AtomicReference 持有 StringBuilder
        AtomicReference<StringBuilder> buffer = new AtomicReference<>(new StringBuilder());

        return llmClient.stream(ctx.getModel(), ctx.getMessages(), ctx.getTemperature(),
                        ctx.getMaxTokens(), Constants.BizType.AGENT, String.valueOf(agentId))
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        buffer.get().append(chunk);
                    }
                })
                .doFinally(signal -> {
                    String full = buffer.get().toString();
                    if (StrUtil.isNotBlank(full)) {
                        finishReply(ctx, full, 0, (int) (System.currentTimeMillis() - start), null);
                    } else if (signal == SignalType.ON_ERROR) {
                        saveFailedReply(ctx, "流式调用失败", start);
                    }
                });
    }

    // ============================ 会话 / 消息 ============================

    @Override
    public PageResult<AgentConversation> selectConversations(PageQuery query, AgentConversation q) {
        IPage<AgentConversation> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AgentConversation> result = conversationMapper.selectConversationPage(page,
                q == null ? new AgentConversation() : q);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<AgentMessage> selectMessages(PageQuery query, AgentMessage q) {
        AgentMessage params = q == null ? new AgentMessage() : q;
        // 前端传的是 conversationId，这里做别名归一化
        if (params.getConvId() == null && params.getConversationId() != null) {
            params.setConvId(params.getConversationId());
        }
        IPage<AgentMessage> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AgentMessage> result = messageMapper.selectMessagePage(page, params);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteConversations(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        messageMapper.deleteByConvIds(ids);
        return conversationMapper.deleteByIds(ids);
    }

    // ============================ 私有方法 ============================

    /**
     * 对话前的统一预处理：校验智能体与会话、落库用户消息、组装上下文、加载模型
     */
    private ChatContext prepareContext(Long agentId, Long conversationId, String content) {
        if (agentId == null) {
            throw new BusinessException("智能体不存在");
        }
        AgentDefinition agent = agentMapper.selectDetail(agentId);
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("发送内容不能为空");
        }

        Long convId = conversationId;
        if (convId == null) {
            convId = createConversation(agentId, content);
        } else {
            AgentConversation conv = conversationMapper.selectById(convId);
            if (conv == null) {
                throw new BusinessException("会话不存在");
            }
            if (!agentId.equals(conv.getAgentId())) {
                throw new BusinessException("会话不属于该智能体");
            }
        }

        // 用户消息先落库：即便后续大模型调用失败，刷新页面也能看到这条提问
        saveUserMessage(convId, content);

        ChatContext ctx = new ChatContext();
        ctx.setAgent(agent);
        ctx.setConvId(convId);
        ctx.setMessages(buildMessages(agent, convId));

        LlmModel model = llmClient.getOrLoadModel(agent.getModelId());
        ctx.setModel(model);
        ctx.setTemperature(agent.getTemperature() != null ? agent.getTemperature() : model.getTemperature());
        ctx.setMaxTokens(agent.getMaxTokens() != null ? agent.getMaxTokens() : model.getMaxTokens());
        return ctx;
    }

    /**
     * 组装发送给大模型的消息列表：系统提示词 + 最近 N 条历史（含本次提问）
     */
    private List<LlmMessage> buildMessages(AgentDefinition agent, Long convId) {
        List<LlmMessage> messages = new ArrayList<>();
        String systemPrompt = StrUtil.isNotBlank(agent.getSystemPrompt())
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;
        // 把该智能体挂载的技能正文追加进系统提示词，让模型具备对应领域的做事方法
        try {
            String skillPrompt = skillService.buildSkillPrompt(
                    com.aip.platform.skill.entity.SkillBinding.BizType.AGENT, agent.getAgentId());
            if (StrUtil.isNotBlank(skillPrompt)) {
                systemPrompt = systemPrompt + skillPrompt;
            }
        } catch (Exception e) {
            // 技能加载失败不能影响正常对话，记录日志后继续
            log.warn("加载智能体技能失败 agentId={}: {}", agent.getAgentId(), e.getMessage());
        }
        // 系统提示词始终放在最前面
        messages.add(LlmMessage.system(systemPrompt));

        List<AgentMessage> history = messageMapper.selectLatestMessages(convId, HISTORY_LIMIT);
        if (history != null) {
            for (AgentMessage item : history) {
                if (StrUtil.isBlank(item.getContent())) {
                    continue;
                }
                if (AgentMessage.ROLE_ASSISTANT.equals(item.getRole())) {
                    messages.add(LlmMessage.assistant(item.getContent()));
                } else if (!AgentMessage.ROLE_SYSTEM.equals(item.getRole())) {
                    messages.add(LlmMessage.user(item.getContent()));
                }
            }
        }
        return messages;
    }

    // ============================ 脚本工具调用 ============================

    /**
     * 调用大模型，支持脚本工具循环：
     * 模型输出工具调用块 → 执行脚本 → 把结果作为上下文回灌 → 再次调用模型，直到给出最终答案。
     * 没有可用脚本时退化为单次普通调用，行为与改造前完全一致。
     */
    private LlmResponse callModelWithTools(ChatContext ctx) {
        List<SkillScript> scripts = loadScripts(ctx.getAgent().getAgentId());
        if (scripts.isEmpty()) {
            return llmClient.chat(ctx.getModel(), ctx.getMessages(), ctx.getTemperature(),
                    ctx.getMaxTokens(), Constants.BizType.AGENT, String.valueOf(ctx.getAgent().getAgentId()));
        }

        List<LlmMessage> messages = ctx.getMessages();
        int rounds = maxToolRounds > 0 ? maxToolRounds : 3;
        int totalTokens = 0;
        int totalCost = 0;
        LlmResponse last = null;

        for (int i = 0; i < rounds; i++) {
            LlmResponse resp = llmClient.chat(ctx.getModel(), messages, ctx.getTemperature(),
                    ctx.getMaxTokens(), Constants.BizType.AGENT, String.valueOf(ctx.getAgent().getAgentId()));
            if (resp == null) {
                return null;
            }
            totalTokens += resp.getTotalTokens() == null ? 0 : resp.getTotalTokens();
            totalCost += resp.getCostMs() == null ? 0 : resp.getCostMs();
            if (!resp.isSuccess()) {
                return resp;
            }
            last = resp;

            ToolCall call = parseToolCall(resp.getContent());
            if (call == null) {
                // 没有调用指令，说明已经是最终答案
                resp.setTotalTokens(totalTokens);
                resp.setCostMs(totalCost);
                return resp;
            }

            log.info("智能体调用脚本 agentId={} tool={} args={}", ctx.getAgent().getAgentId(), call.tool, call.args);
            ScriptResult result = scriptService.runByCode(scripts, call.tool, call.args,
                    ISkillScriptService.BIZ_AGENT, String.valueOf(ctx.getAgent().getAgentId()));

            // 把模型的调用意图与脚本结果都回灌进上下文，模型才能接着生成最终答案
            messages.add(LlmMessage.assistant(resp.getContent()));
            messages.add(LlmMessage.user("工具「" + call.tool + "」的执行结果如下：\n"
                    + result.toPromptText()
                    + "\n（这是脚本真实执行的输出，请基于它回答用户；如还需调用其它工具，"
                    + "继续按之前的格式输出工具调用块。）"));
        }

        // 轮次用尽，强制模型收口给最终答案
        messages.add(LlmMessage.user("已达到工具调用次数上限，不要再调用工具，请直接基于已有结果给出最终答案。"));
        LlmResponse resp = llmClient.chat(ctx.getModel(), messages, ctx.getTemperature(),
                ctx.getMaxTokens(), Constants.BizType.AGENT, String.valueOf(ctx.getAgent().getAgentId()));
        if (resp != null) {
            totalTokens += resp.getTotalTokens() == null ? 0 : resp.getTotalTokens();
            totalCost += resp.getCostMs() == null ? 0 : resp.getCostMs();
            resp.setTotalTokens(totalTokens);
            resp.setCostMs(totalCost);
        }
        return resp;
    }

    /** 判断该智能体是否挂载了可调用的脚本（决定流式链路是否需要先跑工具循环） */
    private boolean hasCallableScripts(ChatContext ctx) {
        try {
            return !loadScripts(ctx.getAgent().getAgentId()).isEmpty();
        } catch (Exception e) {
            log.debug("检查智能体脚本失败: {}", e.getMessage());
            return false;
        }
    }

    /** 加载智能体通过技能挂载的全部可用脚本 */
    private List<SkillScript> loadScripts(Long agentId) {
        if (agentId == null) {
            return Collections.emptyList();
        }
        try {
            List<Long> skillIds = skillService.selectSkillIdsByBiz(
                    com.aip.platform.skill.entity.SkillBinding.BizType.AGENT, agentId);
            if (skillIds == null || skillIds.isEmpty()) {
                return Collections.emptyList();
            }
            List<SkillScript> scripts = scriptService.selectEnabledBySkillIds(skillIds);
            return scripts == null ? Collections.emptyList() : scripts;
        } catch (Exception e) {
            log.warn("加载智能体脚本失败 agentId={}: {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从模型输出中解析脚本调用块：
     * <pre>```tool
     * {"tool": "脚本编码", "args": {...}}
     * ```</pre>
     */
    private ToolCall parseToolCall(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        // 1) 优先匹配规范写法：```tool {json} ```
        Matcher m = TOOL_BLOCK.matcher(text);
        while (m.find()) {
            ToolCall call = parseToolJson(m.group(1));
            if (call != null) {
                return call;
            }
        }
        // 2) 兜底：部分模型会省略代码块，直接输出裸 JSON，此时要求整段就是一个调用指令
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseToolJson(trimmed);
        }
        return null;
    }

    /** 从一段文本中截出 JSON 并解析成工具调用指令 */
    private ToolCall parseToolJson(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body.substring(start, end + 1));
            String tool = node.path("tool").asText(null);
            if (StrUtil.isBlank(tool)) {
                return null;
            }
            JsonNode args = node.get("args");
            String argsJson = args == null || args.isNull() ? "{}" : objectMapper.writeValueAsString(args);
            return new ToolCall(tool.trim(), argsJson);
        } catch (Exception e) {
            log.debug("解析工具调用块失败: {}", e.getMessage());
            return null;
        }
    }

    /** 把最终答案切成小片，让前端仍有逐步输出的效果 */
    private List<String> splitForStream(String text) {
        String content = text == null ? "" : text;
        if (content.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < content.length(); i += STREAM_CHUNK_SIZE) {
            chunks.add(content.substring(i, Math.min(i + STREAM_CHUNK_SIZE, content.length())));
        }
        return chunks;
    }

    /** 一次脚本调用指令 */
    private static class ToolCall {
        private final String tool;
        private final String args;

        ToolCall(String tool, String args) {
            this.tool = tool;
            this.args = args;
        }
    }

    /**
     * 新建会话，标题取提问内容的前 20 个字符
     */
    private Long createConversation(Long agentId, String content) {
        AgentConversation conv = new AgentConversation();
        conv.setAgentId(agentId);
        conv.setUserId(SecurityUtils.getUserId());
        conv.setTitle(buildTitle(content));
        conv.setMessageCount(0);
        conv.setStatus(AgentConversation.STATUS_NORMAL);
        LocalDateTime now = LocalDateTime.now();
        conv.setCreateTime(now);
        conv.setUpdateTime(now);
        conversationMapper.insert(conv);
        return conv.getConvId();
    }

    private String buildTitle(String content) {
        String text = content == null ? "" : content.trim().replace("\n", " ");
        return text.length() <= TITLE_MAX_LEN ? text : text.substring(0, TITLE_MAX_LEN);
    }

    /**
     * 落库用户消息
     */
    private void saveUserMessage(Long convId, String content) {
        AgentMessage msg = new AgentMessage();
        msg.setConvId(convId);
        msg.setRole(AgentMessage.ROLE_USER);
        msg.setContent(content);
        msg.setTokens(0);
        msg.setCostMs(0);
        msg.setStatus(AgentMessage.STATUS_NORMAL);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 落库失败的助手消息，并回写会话消息数
     */
    private void saveFailedReply(ChatContext ctx, String errorMsg, long startMs) {
        try {
            AgentMessage msg = new AgentMessage();
            msg.setConvId(ctx.getConvId());
            msg.setRole(AgentMessage.ROLE_ASSISTANT);
            msg.setContent("");
            msg.setStatus(AgentMessage.STATUS_FAIL);
            msg.setErrorMsg(truncate(errorMsg, ERROR_MSG_MAX_LEN));
            msg.setModelName(ctx.getModel() == null ? null : ctx.getModel().getModelName());
            msg.setTokens(0);
            msg.setCostMs((int) Math.max(0, System.currentTimeMillis() - startMs));
            msg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(msg);
            incrMessageCount(ctx.getConvId());
        } catch (Exception e) {
            log.warn("记录失败消息异常 convId={}: {}", ctx.getConvId(), e.getMessage());
        }
    }

    /**
     * 落库成功的助手消息，回写会话统计，并组装返回 DTO
     */
    private AgentReplyDTO finishReply(ChatContext ctx, String content, int tokens,
                                      Integer costMs, String responseModelName) {
        String modelName = StrUtil.isNotBlank(responseModelName)
                ? responseModelName
                : (ctx.getModel() == null ? null : ctx.getModel().getModelName());

        AgentMessage reply = new AgentMessage();
        reply.setConvId(ctx.getConvId());
        reply.setRole(AgentMessage.ROLE_ASSISTANT);
        reply.setContent(content == null ? "" : content);
        reply.setTokens(tokens);
        reply.setModelName(modelName);
        reply.setCostMs(costMs == null ? 0 : costMs);
        reply.setStatus(AgentMessage.STATUS_NORMAL);
        reply.setCreateTime(LocalDateTime.now());
        messageMapper.insert(reply);

        incrMessageCount(ctx.getConvId());

        AgentReplyDTO dto = new AgentReplyDTO();
        dto.setConversationId(ctx.getConvId());
        dto.setMessageId(reply.getMsgId());
        dto.setContent(reply.getContent());
        dto.setModelName(modelName);
        dto.setTokens(tokens);
        dto.setCostMs(reply.getCostMs());
        dto.setSuccess(Boolean.TRUE);
        return dto;
    }

    /**
     * 会话消息数 +2（一问一答）并刷新更新时间
     */
    private void incrMessageCount(Long convId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<AgentConversation>()
                .eq(AgentConversation::getConvId, convId)
                .setSql("message_count = message_count + 2")
                .set(AgentConversation::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 别名归一化：agentKey -&gt; agentCode、tools 数组 -&gt; toolsJson
     */
    private void normalize(AgentDefinition agent) {
        if (agent == null) {
            return;
        }
        if (StrUtil.isNotBlank(agent.getAgentKey()) && StrUtil.isBlank(agent.getAgentCode())) {
            agent.setAgentCode(agent.getAgentKey());
        }
        if (StrUtil.isBlank(agent.getAgentKey()) && StrUtil.isNotBlank(agent.getAgentCode())) {
            agent.setAgentKey(agent.getAgentCode());
        }
        if (agent.getTools() != null && !agent.getTools().isEmpty()) {
            agent.setToolsJson(toJson(agent.getTools()));
        }
    }

    /**
     * 反序列化 toolsJson，供前端直接使用数组
     */
    private void fillTools(AgentDefinition agent) {
        if (agent == null || StrUtil.isBlank(agent.getToolsJson())) {
            return;
        }
        try {
            agent.setTools(objectMapper.readValue(agent.getToolsJson(), new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            log.debug("解析工具配置失败 agentId={}: {}", agent.getAgentId(), e.getMessage());
        }
    }

    /**
     * 回填智能体挂载的技能ID与技能名称，供前端回显
     */
    private void fillSkills(AgentDefinition agent) {
        if (agent == null || agent.getAgentId() == null) {
            return;
        }
        try {
            List<Long> ids = skillService.selectSkillIdsByBiz(
                    com.aip.platform.skill.entity.SkillBinding.BizType.AGENT, agent.getAgentId());
            agent.setSkillIds(ids == null ? new ArrayList<Long>() : ids);
            if (ids != null && !ids.isEmpty()) {
                List<com.aip.platform.skill.entity.Skill> skills = skillService.selectByBiz(
                        com.aip.platform.skill.entity.SkillBinding.BizType.AGENT, agent.getAgentId());
                if (skills != null && !skills.isEmpty()) {
                    agent.setSkillNames(skills.stream()
                            .map(s -> s.getSkillName())
                            .collect(Collectors.joining("、")));
                }
            }
        } catch (Exception e) {
            log.debug("加载智能体技能失败 agentId={}: {}", agent.getAgentId(), e.getMessage());
        }
    }

    private String toJson(List<String> tools) {
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            log.warn("序列化工具配置失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 校验编码唯一性
     */
    private void checkCodeUnique(AgentDefinition agent) {
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getAgentCode, agent.getAgentCode());
        if (agent.getAgentId() != null) {
            wrapper.ne(AgentDefinition::getAgentId, agent.getAgentId());
        }
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("智能体编码[" + agent.getAgentCode() + "]已存在");
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 一次对话的上下文快照
     */
    @Data
    private static class ChatContext {

        private AgentDefinition agent;

        private LlmModel model;

        private Long convId;

        private List<LlmMessage> messages;

        private Double temperature;

        private Integer maxTokens;
    }
}
