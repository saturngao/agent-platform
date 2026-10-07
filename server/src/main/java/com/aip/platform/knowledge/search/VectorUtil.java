package com.aip.platform.knowledge.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 向量序列化与相似度计算
 *
 * <p>向量以 JSON float 数组形式存放在 kb_chunk.embedding（MySQL 无向量类型）。</p>
 */
@Slf4j
public final class VectorUtil {

    private VectorUtil() {
    }

    /** 余弦相似度，任一为空返回 0 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0d, na = 0d, nb = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String toJson(float[] v) {
        if (v == null || v.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public static float[] fromJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            float[] arr = mapper.readValue(json, float[].class);
            return arr.length == 0 ? null : arr;
        } catch (Exception e) {
            return null;
        }
    }
}
