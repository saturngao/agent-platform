package com.aip.platform.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.knowledge.entity.KnowledgeBase;
import com.aip.platform.knowledge.entity.KnowledgeDocument;
import com.aip.platform.knowledge.file.KnowledgeFileStore;
import com.aip.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.platform.knowledge.mapper.KnowledgeChunkMapper;
import com.aip.platform.knowledge.mapper.KnowledgeDocumentMapper;
import com.aip.platform.knowledge.service.IKnowledgeBaseService;
import com.aip.platform.llm.entity.LlmModel;
import com.aip.platform.llm.mapper.LlmModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements IKnowledgeBaseService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final LlmModelMapper modelMapper;
    private final KnowledgeFileStore fileStore;

    @Override
    public PageResult<KnowledgeBase> selectPage(PageQuery query, KnowledgeBase params) {
        IPage<KnowledgeBase> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getDelFlag, 0);
        if (params != null) {
            if (StrUtil.isNotBlank(params.getBaseName())) {
                wrapper.like(KnowledgeBase::getBaseName, params.getBaseName().trim());
            }
            if (StrUtil.isNotBlank(params.getBaseCode())) {
                wrapper.like(KnowledgeBase::getBaseCode, params.getBaseCode().trim());
            }
            if (params.getStatus() != null) {
                wrapper.eq(KnowledgeBase::getStatus, params.getStatus());
            }
        }
        wrapper.orderByDesc(KnowledgeBase::getBaseId);
        IPage<KnowledgeBase> result = baseMapper.selectPage(page, wrapper);
        result.getRecords().forEach(this::fillExtra);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public KnowledgeBase selectDetail(Long baseId) {
        if (baseId == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        KnowledgeBase base = baseMapper.selectById(baseId);
        if (base == null || (base.getDelFlag() != null && base.getDelFlag() == 2)) {
            throw new BusinessException("知识库不存在或已删除");
        }
        fillExtra(base);
        return base;
    }

    @Override
    public List<Map<String, Object>> selectOptions() {
        List<KnowledgeBase> list = baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDelFlag, 0)
                .eq(KnowledgeBase::getStatus, 0)
                .orderByDesc(KnowledgeBase::getBaseId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeBase b : list) {
            Map<String, Object> m = new HashMap<>(6);
            m.put("baseId", b.getBaseId());
            m.put("baseCode", b.getBaseCode());
            m.put("baseName", b.getBaseName());
            m.put("docCount", b.getDocCount());
            m.put("chunkCount", b.getChunkCount());
            m.put("vectorReady", Boolean.TRUE.equals(b.getVectorReady()) || chunkMapper.countWithEmbedding(b.getBaseId()) > 0);
            out.add(m);
        }
        return out;
    }

    @Override
    public int insertBase(KnowledgeBase base) {
        validate(base, true);
        base.setBaseId(null);
        base.setDelFlag(0);
        if (base.getStatus() == null) {
            base.setStatus(0);
        }
        if (base.getChunkSize() == null || base.getChunkSize() <= 0) {
            base.setChunkSize(500);
        }
        if (base.getChunkOverlap() == null || base.getChunkOverlap() < 0) {
            base.setChunkOverlap(80);
        }
        if (base.getTopK() == null || base.getTopK() <= 0) {
            base.setTopK(5);
        }
        if (base.getScoreThreshold() == null) {
            base.setScoreThreshold(new java.math.BigDecimal("0.15"));
        }
        base.setDocCount(0);
        base.setChunkCount(0);
        // 目录提前建好，方便直接往里放文件
        fileStore.baseDir(base.getBaseCode());
        return baseMapper.insert(base);
    }

    @Override
    public int updateBase(KnowledgeBase base) {
        if (base == null || base.getBaseId() == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        KnowledgeBase exist = selectDetail(base.getBaseId());
        validate(base, false);
        // 编码不可变更（磁盘目录名与编码绑定）
        base.setBaseCode(null);
        base.setDelFlag(null);
        Integer rows = baseMapper.updateById(base) > 0 ? 1 : 0;
        // 换了 embedding 模型就清掉旧向量，避免不同模型的向量混用导致相似度失真
        if (base.getEmbedModelId() != null && !base.getEmbedModelId().equals(exist.getEmbedModelId())) {
            clearEmbedding(base.getBaseId());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBases(List<Long> baseIds) {
        if (baseIds == null || baseIds.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long id : baseIds) {
            KnowledgeBase base = baseMapper.selectById(id);
            if (base == null) {
                continue;
            }
            chunkMapper.deleteByBaseId(id);
            // 文档逻辑删除，保留记录便于审计；磁盘原文一并清理
            for (KnowledgeDocument d : documentMapper.selectByBaseId(id)) {
                KnowledgeDocument up = new KnowledgeDocument();
                up.setDocId(d.getDocId());
                up.setDelFlag(2);
                documentMapper.updateById(up);
                fileStore.delete(d.getStorePath());
            }
            fileStore.deleteBaseDir(base.getBaseCode());
            base.setDelFlag(2);
            baseMapper.updateById(base);
            n++;
        }
        return n;
    }

    @Override
    public void recount(Long baseId) {
        if (baseId == null) {
            return;
        }
        baseMapper.recount(baseId);
    }

    private void clearEmbedding(Long baseId) {
        List<com.aip.platform.knowledge.entity.KnowledgeChunk> chunks = chunkMapper.selectByBaseId(baseId, 100000);
        int n = 0;
        for (com.aip.platform.knowledge.entity.KnowledgeChunk c : chunks) {
            if (c.getEmbedding() != null && !c.getEmbedding().isBlank()) {
                c.setEmbedding(null);
                c.setEmbedModel(null);
                chunkMapper.updateById(c);
                n++;
            }
        }
        log.info("[知识库] 清空向量 baseId={} 清除{}条", baseId, n);
    }

    private void fillExtra(KnowledgeBase base) {
        if (base == null) {
            return;
        }
        if (base.getEmbedModelId() != null) {
            LlmModel model = modelMapper.selectById(base.getEmbedModelId());
            if (model != null) {
                base.setEmbedModelName(model.getModelName());
            }
        }
        base.setVectorReady(chunkMapper.countWithEmbedding(base.getBaseId()) > 0);
    }

    private void validate(KnowledgeBase base, boolean isAdd) {
        if (base == null) {
            throw new BusinessException("参数不能为空");
        }
        if (StrUtil.isBlank(base.getBaseName())) {
            throw new BusinessException("知识库名称不能为空");
        }
        if (isAdd) {
            if (StrUtil.isBlank(base.getBaseCode())) {
                base.setBaseCode(toCode(base.getBaseName()));
            }
            if (StrUtil.isBlank(base.getBaseCode())) {
                base.setBaseCode("kb-" + System.currentTimeMillis() % 100000);
            }
            KnowledgeBase exist = baseMapper.selectByCode(base.getBaseCode());
            if (exist != null) {
                throw new BusinessException("知识库编码已存在：" + base.getBaseCode());
            }
        }
        if (base.getChunkSize() != null && base.getChunkOverlap() != null
                && base.getChunkOverlap() >= base.getChunkSize()) {
            throw new BusinessException("分片重叠必须小于分片大小");
        }
    }

    private String toCode(String name) {
        if (name == null) {
            return null;
        }
        String c = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s]+", "-")
                .replaceAll("[^a-z0-9_\\-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        return c.isEmpty() ? null : c;
    }

}
