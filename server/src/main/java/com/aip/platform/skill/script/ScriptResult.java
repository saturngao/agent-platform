package com.aip.platform.skill.script;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 脚本执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否执行成功（退出码为 0 且未超时） */
    private boolean success;

    /** 进程退出码 */
    private Integer exitCode;

    /** 标准输出（已截断） */
    private String stdout;

    /** 错误输出（已截断） */
    private String stderr;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 是否因超时被强制终止 */
    private boolean timedOut;

    /** 执行失败原因（脚本本身报错时为空，错误在 stderr 里） */
    private String errorMsg;

    /** 输出是否被截断 */
    private boolean truncated;

    public static ScriptResult fail(String errorMsg) {
        return ScriptResult.builder()
                .success(false)
                .exitCode(-1)
                .stdout("")
                .stderr("")
                .costMs(0)
                .timedOut(false)
                .errorMsg(errorMsg)
                .truncated(false)
                .build();
    }

    /**
     * 给大模型看的精简结果，避免把大量无关输出塞回上下文
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("执行").append(success ? "成功" : "失败");
        if (timedOut) {
            sb.append("（超时被终止）");
        }
        sb.append("，耗时 ").append(costMs == null ? 0 : costMs).append("ms，退出码 ").append(exitCode).append("\n");
        if (stdout != null && !stdout.isEmpty()) {
            sb.append("输出：\n").append(stdout).append("\n");
        }
        if (stderr != null && !stderr.isEmpty()) {
            sb.append("错误信息：\n").append(stderr).append("\n");
        }
        if (errorMsg != null && !errorMsg.isEmpty()) {
            sb.append("说明：").append(errorMsg).append("\n");
        }
        if (truncated) {
            sb.append("（输出内容过长已截断）\n");
        }
        return sb.toString();
    }
}
