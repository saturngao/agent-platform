package com.aip.platform.workflow.engine;

import com.aip.platform.workflow.dto.GraphNode;

import java.util.Collections;
import java.util.Map;

/**
 * 节点配置读取工具
 *
 * <p>画布里 data 的值可能是字符串也可能是数字（前端表单难以保证），
 * 这里统一做一次宽容的类型转换，避免各执行器重复写一堆判空代码。</p>
 */
public final class NodeData {

    private NodeData() {
    }

    /** 取节点配置，永远不为 null */
    public static Map<String, Object> of(GraphNode node) {
        if (node == null || node.getData() == null) {
            return Collections.emptyMap();
        }
        return node.getData();
    }

    /** 转字符串：null 返回空串 */
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 转 Double，无法解析返回 null */
    public static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 转 Integer，无法解析返回 null */
    public static Integer toInteger(Object value) {
        Double number = toDouble(value);
        return number == null ? null : number.intValue();
    }

    /** 转 Long，无法解析返回 null */
    public static Long toLong(Object value) {
        Double number = toDouble(value);
        return number == null ? null : number.longValue();
    }
}
