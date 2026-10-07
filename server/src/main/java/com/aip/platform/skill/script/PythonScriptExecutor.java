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
 * <p>两种执行形态：</p>
 * <ul>
 *   <li>{@link #runFile(Path, String, String, Integer)}：执行技能目录下的脚本文件，
 *       进程工作目录就是技能目录，因此脚本可以相对路径读取资源文件（references/、assets/），
 *       也可以 import 同目录或子目录里的其它 .py 模块，这是推荐形态；</li>
 *   <li>{@link #run(String, String, Integer)}：执行一段源码字符串，写入临时目录后执行，
 *       用于在线调试与兼容早期把源码存数据库的脚本。</li>
 * </ul>
 *
 * <p>基础防护：</p>
 * <ul>
 *   <li>总开关：platform.skill.python.enabled 关闭后拒绝任何执行</li>
 *   <li>超时中断：超时后强制杀进程</li>
 *   <li>输出截断：stdout / stderr 按字符数截断，避免撑爆模型上下文</li>
 * </ul>
 *
 * <p>入参传递约定：入参 JSON 会写入独立临时目录的 input.json，同时通过环境变量
 * SKILL_PARAMS 传入同样的字符串、通过 SKILL_INPUT_FILE 传入 input.json 的绝对路径，
 * 脚本三选一读取即可。</p>
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

    // ====================== 执行入口 ======================

    /**
     * 执行技能目录下的脚本文件
     *
     * @param skillDir  技能目录（进程工作目录，脚本内可用相对路径访问该目录下的资源）
     * @param relScript 脚本相对技能目录的路径，如 scripts/cost.py
     * @param paramsJson 入参 JSON 字符串，可为 null
     * @param timeoutSec 期望超时秒数
     * @return 执行结果
     */
    public ScriptResult runFile(Path skillDir, String relScript, String paramsJson, Integer timeoutSec) {
        if (!enabled) {
            return ScriptResult.fail("脚本执行功能已关闭，请在服务端配置 platform.skill.python.enabled=true 后重试");
        }
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return ScriptResult.fail("技能目录不存在：" + skillDir);
        }
        if (relScript == null || relScript.trim().isEmpty()) {
            return ScriptResult.fail("脚本路径为空");
        }
        String rel = relScript.replace('\\', '/').trim();
        Path script = skillDir.resolve(rel).normalize();
        if (!script.startsWith(skillDir.normalize())) {
            return ScriptResult.fail("非法的脚本路径：" + relScript);
        }
        if (!Files.isRegularFile(script)) {
            return ScriptResult.fail("脚本文件不存在：" + rel + "，请确认文件已上传或重新扫描技能目录");
        }

        int timeout = resolveTimeout(timeoutSec);
        String params = (paramsJson == null || paramsJson.trim().isEmpty()) ? "{}" : paramsJson.trim();
        Path inputDir = null;
        try {
            inputDir = createWorkDir();
            Path inputFile = inputDir.resolve("input.json");
            Files.writeString(inputFile, params, StandardCharsets.UTF_8);

            List<String> argv = new ArrayList<>();
            argv.add(command);
            argv.add(rel);

            return execute(skillDir, argv, params, inputFile.toString(), timeout);
        } catch (Exception e) {
            log.warn("执行脚本文件失败 {}: {}", rel, e.getMessage());
            return ScriptResult.fail("执行脚本失败：" + e.getMessage());
        } finally {
            deleteQuietly(inputDir);
        }
    }

    /**
     * 执行一段源码字符串（在线调试 / 兼容旧数据）
     *
     * @param code       脚本源码
     * @param paramsJson 入参 JSON 字符串，可为 null
     * @param timeoutSec 期望超时秒数
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
        try {
            dir = createWorkDir();
            Files.writeString(dir.resolve("main.py"), code, StandardCharsets.UTF_8);
            Path inputFile = dir.resolve("input.json");
            Files.writeString(inputFile, params, StandardCharsets.UTF_8);

            List<String> argv = new ArrayList<>();
            argv.add(command);
            argv.add("main.py");

            return execute(dir, argv, params, inputFile.toString(), timeout);
        } catch (Exception e) {
            log.warn("执行 Python 脚本失败: {}", e.getMessage());
            return ScriptResult.fail("执行脚本失败：" + e.getMessage());
        } finally {
            deleteQuietly(dir);
        }
    }

    // ====================== 核心执行 ======================

    private ScriptResult execute(Path cwd, List<String> argv, String params, String inputFile, int timeout) {
        long start = System.currentTimeMillis();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(false);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            pb.environment().put("SKILL_PARAMS", params);
            pb.environment().put("SKILL_INPUT_FILE", inputFile);
            pb.environment().put("SKILL_DIR", cwd.toAbsolutePath().toString());
            // 让脚本可以 import 技能目录下任意子目录里的模块
            String existingPath = pb.environment().get("PYTHONPATH");
            pb.environment().put("PYTHONPATH",
                    (existingPath == null || existingPath.isEmpty()) ? cwd.toAbsolutePath().toString()
                            : cwd.toAbsolutePath() + File.pathSeparator + existingPath);

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
            log.warn("启动 Python 进程失败: {}", e.getMessage());
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
