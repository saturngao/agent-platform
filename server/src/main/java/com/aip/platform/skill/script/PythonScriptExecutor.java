package com.aip.platform.skill.script;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Python 脚本执行器
 *
 * <p>把脚本写入独立临时目录后调用 python 进程执行，具备以下基础防护：</p>
 * <ul>
 *   <li>总开关：配置 platform.skill.python.enabled 关闭后拒绝任何执行</li>
 *   <li>目录隔离：每个脚本一个临时目录，进程工作目录即该目录，脚本只能在此读写</li>
 *   <li>超时中断：单次执行超时上限由配置约束，超时后强制杀进程</li>
 *   <li>输出截断：stdout / stderr 均按字符数截断，避免超长输出撑爆上下文</li>
 * </ul>
 *
 * <p>入参传递约定：脚本同目录会生成 input.json，同时通过环境变量 SKILL_PARAMS 传入同样的 JSON 串，
 * 脚本二选一读取即可。</p>
 */
@Slf4j
@Component
public class PythonScriptExecutor {

    /** 总开关，关闭后所有脚本执行直接拒绝 */
    @Value("${platform.skill.python.enabled:true}")
    private boolean enabled;

    /** python 命令，默认 python3，可配置为绝对路径或虚拟环境解释器 */
    @Value("${platform.skill.python.command:python3}")
    private String command;

    /** 脚本可申请的最大超时秒数，超出该值一律按此上限执行 */
    @Value("${platform.skill.python.max-timeout-sec:120}")
    private int maxTimeoutSec;

    /** 未指定超时时的默认值 */
    @Value("${platform.skill.python.default-timeout-sec:30}")
    private int defaultTimeoutSec;

    /** 单个流的输出字符上限 */
    @Value("${platform.skill.python.max-output-chars:8000}")
    private int maxOutputChars;

    /** 临时目录根，留空则使用系统临时目录 */
    @Value("${platform.skill.python.work-dir:}")
    private String workDir;

    /** 读流线程的兜底等待秒数 */
    private static final int DRAIN_WAIT_SEC = 10;

    private final ExecutorService gobblerPool = Executors.newCachedThreadPool();

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 执行 Python 脚本
     *
     * @param code       脚本源码
     * @param paramsJson 入参 JSON 字符串，可为 null
     * @param timeoutSec 期望超时秒数，为空或越界时取默认值/上限
     * @return 执行结果
     */
    public ScriptResult run(String code, String paramsJson, Integer timeoutSec) {
        if (!enabled) {
            return ScriptResult.fail("脚本执行功能已关闭，请在服务端配置 platform.skill.python.enabled=true 后重试");
        }
        if (code == null || code.trim().isEmpty()) {
            return ScriptResult.fail("脚本内容为空");
        }

        int timeout = resolveTimeout(timeoutSec);
        String params = (paramsJson == null || paramsJson.trim().isEmpty()) ? "{}" : paramsJson.trim();

        Path dir = null;
        long start = System.currentTimeMillis();
        Process process = null;
        try {
            dir = createWorkDir();
            Files.writeString(dir.resolve("main.py"), code, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("input.json"), params, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(command, "main.py");
            pb.directory(dir.toFile());
            pb.redirectErrorStream(false);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            pb.environment().put("SKILL_PARAMS", params);

            final Process proc = pb.start();
            process = proc;

            // 必须并发排空 stdout/stderr，否则管道打满会导致进程阻塞
            Future<String> outFuture = gobblerPool.submit(() -> drain(proc.getInputStream()));
            Future<String> errFuture = gobblerPool.submit(() -> drain(proc.getErrorStream()));

            boolean finished = proc.waitFor(timeout, TimeUnit.SECONDS);
            boolean timedOut = !finished;
            if (timedOut) {
                proc.destroyForcibly();
                proc.waitFor(5, TimeUnit.SECONDS);
            }

            String stdout = safeGet(outFuture);
            String stderr = safeGet(errFuture);
            int exitCode = proc.exitValue();
            int cost = (int) (System.currentTimeMillis() - start);

            boolean truncated = false;
            String out = stdout == null ? "" : stdout;
            String err = stderr == null ? "" : stderr;
            if (out.length() > maxOutputChars) {
                out = out.substring(0, maxOutputChars);
                truncated = true;
            }
            if (err.length() > maxOutputChars) {
                err = err.substring(0, maxOutputChars);
                truncated = true;
            }
            if (timedOut) {
                err = (err.isEmpty() ? "" : err + "\n") + "脚本执行超过 " + timeout + " 秒，已被强制终止";
            }

            return ScriptResult.builder()
                    .success(!timedOut && exitCode == 0)
                    .exitCode(exitCode)
                    .stdout(out)
                    .stderr(err)
                    .costMs(cost)
                    .timedOut(timedOut)
                    .errorMsg(timedOut ? "执行超时" : null)
                    .truncated(truncated)
                    .build();

        } catch (Exception e) {
            int cost = (int) (System.currentTimeMillis() - start);
            log.warn("执行 Python 脚本失败: {}", e.getMessage());
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            if (msg.contains("No such file") || msg.contains("cannot run program") || msg.contains("IOException")) {
                msg = "无法启动 Python 进程，请检查服务端 python 命令配置（platform.skill.python.command，当前为 "
                        + command + "）。原始错误：" + msg;
            }
            return ScriptResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .stdout("")
                    .stderr("")
                    .costMs(cost)
                    .timedOut(false)
                    .errorMsg(msg)
                    .truncated(false)
                    .build();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(dir);
        }
    }

    private int resolveTimeout(Integer timeoutSec) {
        int max = maxTimeoutSec > 0 ? maxTimeoutSec : 120;
        int def = defaultTimeoutSec > 0 ? defaultTimeoutSec : 30;
        if (timeoutSec == null || timeoutSec <= 0) {
            return Math.min(def, max);
        }
        return Math.min(timeoutSec, max);
    }

    private Path createWorkDir() throws Exception {
        if (workDir != null && !workDir.trim().isEmpty()) {
            Path base = Paths.get(workDir.trim());
            Files.createDirectories(base);
            return Files.createTempDirectory(base, "skill-py-");
        }
        return Files.createTempDirectory("skill-py-");
    }

    /** 排空一个流并返回文本内容 */
    private String drain(InputStream in) {
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String safeGet(Future<String> future) {
        try {
            return future.get(DRAIN_WAIT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return "";
        }
    }

    /** 递归删除临时目录，失败不影响主流程 */
    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 忽略删除失败，交给系统临时目录回收
                        }
                    });
        } catch (Exception ignored) {
            // ignore
        }
    }

    /** 供运维排查：返回当前生效的执行配置 */
    public List<String> describeConfig() {
        List<String> lines = new ArrayList<>();
        lines.add("enabled=" + enabled);
        lines.add("command=" + command);
        lines.add("defaultTimeoutSec=" + defaultTimeoutSec);
        lines.add("maxTimeoutSec=" + maxTimeoutSec);
        lines.add("maxOutputChars=" + maxOutputChars);
        lines.add("workDir=" + (workDir == null || workDir.isEmpty() ? "(system tmp)" : workDir));
        File probe = new File(command);
        lines.add("commandResolvable=" + (probe.isAbsolute() ? probe.exists() : resolveInPath(command)));
        return lines;
    }

    private boolean resolveInPath(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String part : path.split(File.pathSeparator)) {
            File f = new File(part, cmd);
            if (f.exists() && f.canExecute()) {
                return true;
            }
        }
        return false;
    }
}
