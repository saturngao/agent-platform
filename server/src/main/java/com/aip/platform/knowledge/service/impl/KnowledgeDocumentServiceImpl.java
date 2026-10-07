package com.aip.platform.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.knowledge.entity.KnowledgeBase;
import com.aip.platform.knowledge.entity.KnowledgeChunk;
import com.aip.platform.knowledge.entity.KnowledgeDocument;
import com.aip.platform.knowledge.file.KnowledgeFileStore;
import com.aip.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.platform.knowledge.mapper.KnowledgeChunkMapper;
import com.aip.platform.knowledge.mapper.KnowledgeDocumentMapper;
import com.aip.platform.knowledge.parser.DocumentParserFactory;
import com.aip.platform.knowledge.parser.ParsedDocument;
import com.aip.platform.knowledge.parser.TextSplitter;
import com.aip.platform.knowledge.search.EmbeddingClient;
import com.aip.platform.knowledge.search.VectorUtil;
import com.aip.platform.knowledge.service.IKnowledgeBaseService;
import com.aip.platform.knowledge.service.IKnowledgeDocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文档服务实现
 *
 * <p>上传链路：落盘 → 解析纯文本 → 切分 → 分片入库 →（可选）生成向量 → 回写统计。
 * 任何一步失败都会把文档标记为「解析失败」并保留原因，不影响知识库本身。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements IKnowledgeDocumentService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeFileStore fileStore;
    private final DocumentParserFactory parserFactory;
    private final TextSplitter splitter;
    private final EmbeddingClient embeddingClient;
    private final IKnowledgeBaseService baseService;

    /** 单次最多为多少条分片生成向量，防止超大文档长时间占用 */
    @Value("${platform.knowledge.embed-batch-max:500}")
    private int embedBatchMax;

    @Override
    public List<KnowledgeDocument> selectByBaseId(Long baseId) {
        if (baseId == null) {
            return List.of();
        }
        List<KnowledgeDocument> list = baseMapper.selectByBaseId(baseId);
        list.forEach(this::fillExtra);
        return list;
    }

    @Override
    public KnowledgeDocument selectDetail(Long docId) {
        KnowledgeDocument doc = baseMapper.selectById(docId);
        if (doc == null || (doc.getDelFlag() != null && doc.getDelFlag() == 2)) {
            throw new BusinessException("文档不存在或已删除");
        }
        fillExtra(doc);
        return doc;
    }

    @Override
    public KnowledgeDocument upload(Long baseId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        KnowledgeBase base = knowledgeBaseMapper.selectById(baseId);
        if (base == null || (base.getDelFlag() != null && base.getDelFlag() == 2)) {
            throw new BusinessException("知识库不存在或已删除");
        }
        String fileName = StrUtil.trimToEmpty(file.getOriginalFilename());
        if (fileName.isEmpty()) {
            throw new BusinessException("无法获取文件名");
        }
        if (file.getSize() > DocumentParserFactory.MAX_FILE_SIZE) {
            throw new BusinessException("文件超过 " + (DocumentParserFactory.MAX_FILE_SIZE / 1024 / 1024) + "MB 上限");
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setBaseId(baseId);
        doc.setDocName(fileName);
        doc.setFileType(DocumentParserFactory.extOf(fileName));
        doc.setFileSize(file.getSize());
        doc.setParseStatus(1);
        doc.setParseMsg("解析中");
        doc.setStatus(0);
        doc.setDelFlag(0);
        baseMapper.insert(doc);

        String storePath;
        try (InputStream in = file.getInputStream()) {
            storePath = fileStore.save(base.getBaseCode(), fileName, in);
        } catch (BusinessException e) {
            baseMapper.updateParseStatus(doc.getDocId(), 3, e.getMessage());
            throw e;
        } catch (Exception e) {
            baseMapper.updateParseStatus(doc.getDocId(), 3, "保存文件失败：" + e.getMessage());
            throw new BusinessException("保存文件失败：" + e.getMessage());
        }

        KnowledgeDocument up = new KnowledgeDocument();
        up.setDocId(doc.getDocId());
        up.setStorePath(storePath);
        baseMapper.updateById(up);
        doc.setStorePath(storePath);

        return parseAndStore(doc, base);
    }

    @Override
    public KnowledgeDocument reparse(Long docId) {
        KnowledgeDocument doc = selectDetail(docId);
        KnowledgeBase base = knowledgeBaseMapper.selectById(doc.getBaseId());
        if (base == null) {
            throw new BusinessException("所属知识库不存在");
        }
        baseMapper.updateParseStatus(docId, 1, "解析中");
        return parseAndStore(doc, base);
    }

    /** 解析 → 切分 → 入库 → 可选向量化 */
    private KnowledgeDocument parseAndStore(KnowledgeDocument doc, KnowledgeBase base) {
        try {
            if (StrUtil.isBlank(doc.getStorePath())) {
                throw new BusinessException("文档原文已丢失，请重新上传");
            }
            Path path = fileStore.resolve(doc.getStorePath());
            if (!Files.exists(path)) {
                throw new BusinessException("磁盘上找不到原文，请重新上传");
            }

            ParsedDocument parsed;
            try (InputStream in = Files.newInputStream(path)) {
                parsed = parserFactory.parse(in, doc.getDocName());
            }
            String text = parsed.getText();
            if (StrUtil.isBlank(text)) {
                String msg = "未抽取到文本内容（扫描件/图片型文档需先 OCR，或改用可复制文本的版本）";
                baseMapper.updateParseStatus(doc.getDocId(), 3, msg);
                doc.setParseStatus(3);
                doc.setParseMsg(msg);
                return doc;
            }

            int size = base.getChunkSize() == null || base.getChunkSize() <= 0 ? 500 : base.getChunkSize();
            int overlap = base.getChunkOverlap() == null || base.getChunkOverlap() < 0 ? 80 : base.getChunkOverlap();
            List<String> pieces = splitter.split(text, size, overlap);

            // 先清旧分片，保证重新解析幂等
            chunkMapper.deleteByDocId(doc.getDocId());

            List<KnowledgeChunk> chunks = new ArrayList<>(pieces.size());
            int seq = 0;
            for (String piece : pieces) {
                KnowledgeChunk c = new KnowledgeChunk();
                c.setBaseId(doc.getBaseId());
                c.setDocId(doc.getDocId());
                c.setSeqNo(seq++);
                c.setTitle(titleOf(piece));
                c.setContent(piece);
                c.setCharCount(piece.length());
                c.setCreateTime(LocalDateTime.now());
                chunks.add(c);
            }
            for (KnowledgeChunk c : chunks) {
                chunkMapper.insert(c);
            }

            if (base.getEmbedModelId() != null) {
                embedChunks(chunks, base.getEmbedModelId());
            }

            String msg = "解析成功，共 " + chunks.size() + " 个分片";
            baseMapper.finishParse(doc.getDocId(), text.length(), chunks.size(), msg);
            baseService.recount(doc.getBaseId());

            doc.setCharCount(text.length());
            doc.setChunkCount(chunks.size());
            doc.setParseStatus(2);
            doc.setParseMsg(msg);
            return doc;
        } catch (BusinessException e) {
            baseMapper.updateParseStatus(doc.getDocId(), 3, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[知识库] 解析失败 docId={}", doc.getDocId(), e);
            String msg = "解析失败：" + e.getMessage();
            baseMapper.updateParseStatus(doc.getDocId(), 3, msg);
            throw new BusinessException(msg);
        }
    }

    /** 生成向量并回写；失败只降级为关键词检索，不影响文档解析结果 */
    private void embedChunks(List<KnowledgeChunk> chunks, Long modelId) {
        List<KnowledgeChunk> targets = chunks.stream().limit(embedBatchMax).toList();
        if (targets.isEmpty()) {
            return;
        }
        try {
            List<String> inputs = targets.stream()
                    .map(c -> (StrUtil.isBlank(c.getTitle()) ? "" : c.getTitle() + "\n") + c.getContent())
                    .toList();
            List<float[]> vectors = embeddingClient.embedBatch(modelId, inputs);
            for (int i = 0; i < targets.size() && i < vectors.size(); i++) {
                KnowledgeChunk c = targets.get(i);
                c.setEmbedding(VectorUtil.toJson(vectors.get(i)));
                c.setEmbedModel(String.valueOf(modelId));
                chunkMapper.updateById(c);
            }
        } catch (Exception e) {
            log.warn("[知识库] 生成向量失败，降级为关键词检索：{}", e.getMessage());
        }
    }

    @Override
    public int changeStatus(Long docId, Integer status) {
        if (docId == null || status == null) {
            throw new BusinessException("参数不能为空");
        }
        KnowledgeDocument up = new KnowledgeDocument();
        up.setDocId(docId);
        up.setStatus(status);
        return baseMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocs(List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long id : docIds) {
            KnowledgeDocument doc = baseMapper.selectById(id);
            if (doc == null) {
                continue;
            }
            chunkMapper.deleteByDocId(id);
            fileStore.delete(doc.getStorePath());
            baseMapper.deleteById(id);
            baseService.recount(doc.getBaseId());
            n++;
        }
        return n;
    }

    @Override
    public List<KnowledgeChunk> selectChunks(Long docId) {
        if (docId == null) {
            return List.of();
        }
        List<KnowledgeChunk> list = chunkMapper.selectByDocId(docId);
        // 向量不返回前端，避免响应体过大
        list.forEach(c -> c.setEmbedding(null));
        return list;
    }

    @Override
    public int buildEmbedding(Long baseId) {
        KnowledgeBase base = knowledgeBaseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException("知识库不存在");
        }
        if (base.getEmbedModelId() == null) {
            throw new BusinessException("该知识库未配置 embedding 模型，请先在编辑里选择向量模型");
        }
        if (embeddingClient.resolveModel(base.getEmbedModelId()) == null) {
            throw new BusinessException("所选 embedding 模型不可用，请检查模型配置与状态");
        }
        List<KnowledgeChunk> targets = chunkMapper.selectByBaseId(baseId, embedBatchMax).stream()
                .filter(c -> StrUtil.isBlank(c.getEmbedding()))
                .toList();
        if (targets.isEmpty()) {
            return 0;
        }
        List<String> inputs = targets.stream().map(c -> c.getContent() == null ? "" : c.getContent()).toList();
        List<float[]> vectors = embeddingClient.embedBatch(base.getEmbedModelId(), inputs);
        int n = 0;
        for (int i = 0; i < targets.size() && i < vectors.size(); i++) {
            KnowledgeChunk c = targets.get(i);
            c.setEmbedding(VectorUtil.toJson(vectors.get(i)));
            c.setEmbedModel(String.valueOf(base.getEmbedModelId()));
            chunkMapper.updateById(c);
            n++;
        }
        baseService.recount(baseId);
        return n;
    }

    @Override
    public int clearEmbedding(Long baseId) {
        List<KnowledgeChunk> chunks = chunkMapper.selectByBaseId(baseId, embedBatchMax);
        int n = 0;
        for (KnowledgeChunk c : chunks) {
            if (StrUtil.isNotBlank(c.getEmbedding())) {
                c.setEmbedding(null);
                c.setEmbedModel(null);
                chunkMapper.updateById(c);
                n++;
            }
        }
        return n;
    }

    private void fillExtra(KnowledgeDocument doc) {
        if (doc == null) {
            return;
        }
        long size = doc.getFileSize() == null ? 0 : doc.getFileSize();
        doc.setFileSizeText(size < 1024 ? size + " B"
                : size < 1024 * 1024 ? String.format("%.1f KB", size / 1024.0)
                : String.format("%.1f MB", size / 1024.0 / 1024.0));
    }

    private String titleOf(String piece) {
        if (piece == null) {
            return null;
        }
        for (String line : piece.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > 60 ? t.substring(0, 60) : t;
            }
        }
        return null;
    }
}
