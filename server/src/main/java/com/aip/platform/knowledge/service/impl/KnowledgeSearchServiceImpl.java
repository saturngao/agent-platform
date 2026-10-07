package com.aip.platform.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.knowledge.entity.KnowledgeBase;
import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.aip.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.platform.knowledge.mapper.KnowledgeChunkMapper;
import com.aip.platform.knowledge.search.EmbeddingClient;
import com.aip.platform.knowledge.search.KeywordTokenizer;
import com.aip.platform.knowledge.search.VectorUtil;
import com.aip.platform.knowledge.service.IKnowledgeSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 知识检索实现：关键词粗召回 → 打分 →（可选）向量重排 → 阈值过滤
 *
 * <p>没有配置 embedding 模型、或向量接口调用失败时，自动退化为纯关键词检索，
 * 对调用方（工作流节点 / 智能体）完全透明。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements IKnowledgeSearchService {

    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KeywordTokenizer tokenizer;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    @Value("${platform.knowledge.recall-limit:200}")
    private int recallLimit;

    @Value("${platform.knowledge.rerank-limit:40}")
    private int rerankLimit;

    @Value("${platform.knowledge.keyword-weight:0.45}")
    private double keywordWeight;

    @Value("${platform.knowledge.vector-weight:0.55}")
    private double vectorWeight;

    @Override
    public List<KnowledgeChunk> search(Long baseId, String query, Integer topK) {
        if (baseId == null) {
            return List.of();
        }
        KnowledgeBase base = baseMapper.selectById(baseId);
        if (base == null || (base.getDelFlag() != null && base.getDelFlag() == 2)) {
            return List.of();
        }
        // 停用的知识库不参与检索
        if (base.getStatus() != null && base.getStatus() != 0) {
            return List.of();
        }
        int k = topK == null || topK <= 0
                ? (base.getTopK() == null || base.getTopK() <= 0 ? 5 : base.getTopK())
                : topK;
        String q = query == null ? "" : query.trim();
        List<String> terms = tokenizer.tokenize(q);

        List<KnowledgeChunk> candidates = terms.isEmpty()
                ? chunkMapper.latest(baseId, Math.max(k, 10))
                : chunkMapper.recall(baseId, terms, recallLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 1) 关键词打分（绝对分，不做归一化——否则「全部候选都很差」时最差的也会被拉成满分，
        //    阈值就失去了过滤无关内容的作用）
        double maxKw = 0d;
        for (KnowledgeChunk c : candidates) {
            double kw = keywordScore(c.getContent(), c.getTitle(), terms, q);
            c.setKeywordScore(kw);
            maxKw = Math.max(maxKw, kw);
        }

        // 2) 向量重排（可选，失败自动降级）
        final boolean vectorUsed = base.getEmbedModelId() != null && maxKw > 0
                && rerankByVector(base, candidates, q);
        for (KnowledgeChunk c : candidates) {
            double kw = c.getKeywordScore() == null ? 0d : c.getKeywordScore();
            double vec = c.getVectorScore() == null ? 0d : c.getVectorScore();
            c.setScore(vectorUsed ? keywordWeight * kw + vectorWeight * vec : kw);
        }

        // 3) 阈值 + 截断
        double threshold = base.getScoreThreshold() == null
                ? 0.15 : base.getScoreThreshold().doubleValue();
        List<KnowledgeChunk> hits = candidates.stream()
                .filter(c -> c.getScore() != null && c.getScore() >= threshold)
                .sorted(Comparator.comparing(KnowledgeChunk::getScore).reversed())
                .limit(k)
                .toList();
        if (hits.isEmpty()) {
            return List.of();
        }
        String baseName = base.getBaseName();
        List<KnowledgeChunk> out = new ArrayList<>(hits.size());
        for (KnowledgeChunk c : hits) {
            // 向量不返回给前端/提示词
            c.setEmbedding(null);
            if (StrUtil.isBlank(c.getDocName())) {
                c.setDocName(baseName);
            }
            out.add(c);
        }
        return out;
    }

    @Override
    public List<KnowledgeChunk> searchMulti(List<Long> baseIds, String query, Integer topK) {
        if (baseIds == null || baseIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeChunk> all = new ArrayList<>();
        for (Long id : baseIds) {
            try {
                all.addAll(search(id, query, topK));
            } catch (Exception e) {
                log.warn("[知识库] 检索失败 baseId={}：{}", id, e.getMessage());
            }
        }
        return all.stream()
                .sorted(Comparator.comparing((KnowledgeChunk c) -> c.getScore() == null ? 0d : c.getScore()).reversed())
                .limit(topK == null || topK <= 0 ? 5 : topK)
                .toList();
    }

    @Override
    public String buildContext(List<Long> baseIds, String query, Integer topK, int maxChars) {
        if (baseIds == null || baseIds.isEmpty() || StrUtil.isBlank(query)) {
            return "";
        }
        List<KnowledgeChunk> hits = searchMulti(baseIds, query, topK);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (KnowledgeChunk c : hits) {
            String piece = "【来源：" + (StrUtil.isBlank(c.getDocName()) ? "知识库" : c.getDocName()) + "】\n"
                    + (c.getContent() == null ? "" : c.getContent()) + "\n";
            if (used + piece.length() > maxChars) {
                int remain = maxChars - used;
                if (remain > 80) {
                    sb.append(piece, 0, remain).append("...\n");
                }
                break;
            }
            sb.append(piece).append('\n');
            used += piece.length();
        }
        return sb.toString().trim();
    }

    /**
     * 关键词打分：覆盖率为主，完整问句命中额外加分，长片段轻微稀释
     */
    private double keywordScore(String content, String title, List<String> terms, String rawQuery) {
        if (content == null || content.isEmpty()) {
            return 0d;
        }
        String text = content.toLowerCase(Locale.ROOT);
        if (terms.isEmpty()) {
            return 0d;
        }
        int hit = 0;
        int occurrences = 0;
        for (String t : terms) {
            if (text.contains(t)) {
                hit++;
                occurrences += countOccurrences(text, t);
            }
        }
        if (hit == 0 && title != null && !title.isBlank()) {
            String tl = title.toLowerCase(Locale.ROOT);
            for (String t : terms) {
                if (tl.contains(t)) {
                    hit++;
                }
            }
        }
        if (hit == 0) {
            return 0d;
        }
        double coverage = (double) hit / terms.size();
        double freq = Math.min(1d, occurrences / (double) (terms.size() * 3));
        // 覆盖率取幂：只命中一两个常见词的片段要明显低于覆盖大半关键词的片段，
        // 否则「公司」「制度」这类高频词会把无关文档带进来
        double score = 0.8 * Math.pow(coverage, 1.5) + 0.2 * freq;
        // 完整问句出现，说明这段几乎就是答案
        if (rawQuery != null && rawQuery.length() >= 4 && text.contains(rawQuery.toLowerCase(Locale.ROOT))) {
            score += 0.35;
        }
        // 超长片段稀释：信息密度低
        if (text.length() > 1500) {
            score *= 0.9;
        }
        return Math.min(1d, score);
    }

    private int countOccurrences(String text, String token) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
            if (count > 20) {
                break;
            }
        }
        return count;
    }

    /** 对关键词得分最高的若干条做向量重排，返回是否成功启用 */
    private boolean rerankByVector(KnowledgeBase base, List<KnowledgeChunk> candidates, String query) {
        List<KnowledgeChunk> top = candidates.stream()
                .sorted(Comparator.comparing((KnowledgeChunk c) -> c.getKeywordScore() == null ? 0d : c.getKeywordScore()).reversed())
                .limit(rerankLimit)
                .toList();
        try {
            float[] qv = embeddingClient.embed(base.getEmbedModelId(), query);
            if (qv == null || qv.length == 0) {
                return false;
            }
            // 缺失向量的分片现算并回写
            List<KnowledgeChunk> missing = top.stream().filter(c -> StrUtil.isBlank(c.getEmbedding())).toList();
            if (!missing.isEmpty()) {
                List<String> inputs = missing.stream().map(c -> c.getContent() == null ? "" : c.getContent()).toList();
                List<float[]> vs = embeddingClient.embedBatch(base.getEmbedModelId(), inputs);
                for (int i = 0; i < missing.size() && i < vs.size(); i++) {
                    KnowledgeChunk c = missing.get(i);
                    c.setEmbedding(VectorUtil.toJson(vs.get(i)));
                    c.setEmbedModel(String.valueOf(base.getEmbedModelId()));
                    chunkMapper.updateById(c);
                }
            }
            boolean any = false;
            for (KnowledgeChunk c : top) {
                float[] v = VectorUtil.fromJson(c.getEmbedding(), objectMapper);
                if (v == null) {
                    c.setVectorScore(0d);
                    continue;
                }
                double cos = VectorUtil.cosine(qv, v);
                // 余弦取值范围 [-1,1]，归一化到 0~1
                c.setVectorScore(Math.max(0d, (cos + 1) / 2));
                any = true;
            }
            candidates.forEach(c -> {
                if (c.getVectorScore() == null) {
                    c.setVectorScore(0d);
                }
            });
            return any;
        } catch (Exception e) {
            log.warn("[知识库] 向量重排不可用，使用关键词结果：{}", e.getMessage());
            return false;
        }
    }

    /** 校验查询参数（供 Controller 使用） */
    public static void checkQuery(String query) {
        if (StrUtil.isBlank(query)) {
            throw new BusinessException("查询内容不能为空");
        }
    }
}
