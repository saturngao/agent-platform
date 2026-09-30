package com.aip.platform.workflow.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简条件表达式求值器
 *
 * <p>刻意不引入任何脚本引擎（javax.script / GraalVM / SpEL），只支持以下写法：</p>
 * <ul>
 *   <li>{{var}} == null / != null</li>
 *   <li>{{var}} == 'xxx' / != 'xxx'（也支持双引号）</li>
 *   <li>{{var}} &gt; 0 / &lt; 0 / &gt;= / &lt;= / == 数字</li>
 *   <li>true（默认分支）</li>
 *   <li>多个条件可用 &amp;&amp; / || 连接</li>
 * </ul>
 *
 * <p>防御性约束：任何异常都按「不匹配」返回 false，绝不把流程判空失败变成运行时异常。</p>
 */
public final class SimpleConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleConditionEvaluator.class);

    /** {{变量}} 操作符 右值 */
    private static final Pattern ATOM =
            Pattern.compile("^\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");

    private static final Pattern SINGLE_VAR = Pattern.compile("^\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}$");

    private SimpleConditionEvaluator() {
    }

    /**
     * 判断条件表达式是否成立
     *
     * @param expression 表达式，允许为 null
     * @param context    运行上下文
     * @return true 表示命中该分支；表达式异常或无法识别时一律返回 false
     */
    public static boolean matches(String expression, WorkflowContext context) {
        try {
            if (expression == null || context == null) {
                return false;
            }
            String expr = expression.trim();
            if (expr.isEmpty()) {
                return false;
            }
            // || 优先级最低：任一成立即命中
            if (expr.contains("||")) {
                for (String part : expr.split("\\|\\|")) {
                    if (matches(part, context)) {
                        return true;
                    }
                }
                return false;
            }
            // && ：全部成立才命中
            if (expr.contains("&&")) {
                for (String part : expr.split("&&")) {
                    if (!matches(part, context)) {
                        return false;
                    }
                }
                return true;
            }
            return evalAtom(expr, context);
        } catch (Exception e) {
            LOGGER.warn("条件表达式求值失败，按不匹配处理，表达式=[{}]，原因={}", expression, e.getMessage());
            return false;
        }
    }

    /** 单个原子表达式求值 */
    private static boolean evalAtom(String atom, WorkflowContext context) {
        String text = atom == null ? "" : atom.trim();
        if (text.isEmpty()) {
            return false;
        }
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        Matcher matcher = ATOM.matcher(text);
        if (!matcher.matches()) {
            return truthy(text, context);
        }
        String varName = matcher.group(1);
        String operator = matcher.group(2);
        String right = matcher.group(3).trim();
        Object left = context.getVariable(varName);

        if ("null".equalsIgnoreCase(right)) {
            boolean isNull = left == null;
            return "==".equals(operator) ? isNull : !isNull;
        }
        Object rightValue = parseLiteral(right);
        if (rightValue instanceof Number number) {
            Double leftNumber = toDouble(left);
            if (leftNumber == null) {
                return compareString(operator, stringOrEmpty(left), String.valueOf(number.doubleValue()));
            }
            return compareNumber(operator, leftNumber, number.doubleValue());
        }
        return compareString(operator, stringOrEmpty(left), String.valueOf(rightValue));
    }

    /** 只有 {{var}} 的情况：按其真值判断 */
    private static boolean truthy(String atom, WorkflowContext context) {
        Matcher matcher = SINGLE_VAR.matcher(atom);
        if (!matcher.matches()) {
            LOGGER.debug("无法识别的条件表达式：{}", atom);
            return false;
        }
        Object value = context.getVariable(matcher.group(1));
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return !text.isEmpty() && !"false".equalsIgnoreCase(text);
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /** 右值解析：引号字符串 / 布尔 / 数字 / 其它按字符串处理 */
    private static Object parseLiteral(String raw) {
        String text = raw.trim();
        if (text.length() >= 2
                && ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\"")))) {
            return text.substring(1, text.length() - 1);
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.valueOf(text);
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private static boolean compareNumber(String operator, double left, double right) {
        switch (operator) {
            case ">":
                return left > right;
            case "<":
                return left < right;
            case ">=":
                return left >= right;
            case "<=":
                return left <= right;
            case "==":
                return Double.compare(left, right) == 0;
            case "!=":
                return Double.compare(left, right) != 0;
            default:
                return false;
        }
    }

    private static boolean compareString(String operator, String left, String right) {
        switch (operator) {
            case "==":
                return left.equals(right);
            case "!=":
                return !left.equals(right);
            default:
                Double leftNumber = toDouble(left);
                Double rightNumber = toDouble(right);
                if (leftNumber == null || rightNumber == null) {
                    return false;
                }
                return compareNumber(operator, leftNumber, rightNumber);
        }
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1D : 0D;
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
