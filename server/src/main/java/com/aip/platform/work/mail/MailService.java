package com.aip.platform.work.mail;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.system.service.ISysConfigService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件发送服务（AI 总结推送用）
 *
 * <p>SMTP 配置优先级：
 * 1）系统参数表（sys.mail.host / port / username / password / fromName）—— 可在运行时动态维护；
 * 2）application.yml 中 spring.mail.* 注入的 {@link JavaMailSender}。</p>
 *
 * <p>发送失败一律抛出 {@link BusinessException}，不做静默吞异常。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    /** 系统参数：SMTP 主机 */
    public static final String KEY_HOST = "sys.mail.host";
    /** 系统参数：SMTP 端口 */
    public static final String KEY_PORT = "sys.mail.port";
    /** 系统参数：发件账号 */
    public static final String KEY_USERNAME = "sys.mail.username";
    /** 系统参数：发件密码/授权码 */
    public static final String KEY_PASSWORD = "sys.mail.password";
    /** 系统参数：发件人显示名 */
    public static final String KEY_FROM_NAME = "sys.mail.fromName";

    private static final int DEFAULT_PORT = 465;

    private final ISysConfigService configService;

    /** 兜底：Spring 自动配置的 JavaMailSender（未配置 spring.mail.host 时可能不存在） */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * 发送 HTML 邮件
     *
     * @param to          收件人数组，不能为空
     * @param cc          抄送数组，可为空
     * @param subject     主题
     * @param htmlContent HTML 正文
     */
    public void sendHtml(String[] to, String[] cc, String subject, String htmlContent) {
        if (to == null || to.length == 0) {
            throw new BusinessException("收件人不能为空");
        }
        MailConfig config = loadConfig();
        boolean dynamic = config.complete();
        JavaMailSender sender = dynamic ? buildSender(config) : mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new BusinessException("邮件服务未配置，请在【系统参数】中维护 sys.mail.* 或检查 application.yml 的 spring.mail 配置");
        }

        String from = dynamic ? config.username : resolveDefaultFrom(sender);
        if (StrUtil.isBlank(from)) {
            throw new BusinessException("邮件发件人账号未配置");
        }
        String personal = dynamic
                ? (StrUtil.isNotBlank(config.fromName) ? config.fromName : config.username)
                : null;

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (StrUtil.isNotBlank(personal)) {
                helper.setFrom(from, personal);
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to);
            if (cc != null && cc.length > 0) {
                helper.setCc(cc);
            }
            helper.setSubject(StrUtil.isBlank(subject) ? "AI 工作总结" : subject);
            helper.setText(htmlContent == null ? "" : htmlContent, true);
            sender.send(message);
            log.info("HTML 邮件已发送，收件人 {} 人，主题：{}", to.length, subject);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage(), e);
            throw new BusinessException("邮件发送失败: " + e.getMessage());
        }
    }

    /**
     * 邮件服务是否已配置（供前端提示用）
     *
     * @return true 表示已配置 SMTP
     */
    public boolean isConfigured() {
        MailConfig config = loadConfig();
        if (config.complete()) {
            return true;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        return sender instanceof JavaMailSenderImpl impl && StrUtil.isNotBlank(impl.getHost());
    }

    /** 读取系统参数中的 SMTP 配置，读取异常时回退为空配置 */
    private MailConfig loadConfig() {
        MailConfig config = new MailConfig();
        try {
            config.host = configService.selectConfigValueByKey(KEY_HOST);
            config.port = parsePort(configService.selectConfigValueByKey(KEY_PORT));
            config.username = configService.selectConfigValueByKey(KEY_USERNAME);
            config.password = configService.selectConfigValueByKey(KEY_PASSWORD);
            config.fromName = configService.selectConfigValueByKey(KEY_FROM_NAME);
        } catch (Exception e) {
            log.warn("读取邮件系统参数失败，回退到 application.yml 配置: {}", e.getMessage());
        }
        return config;
    }

    /** 动态构建 JavaMailSenderImpl（系统参数方式） */
    private JavaMailSenderImpl buildSender(MailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host);
        sender.setPort(config.port);
        sender.setUsername(config.username);
        sender.setPassword(config.password);
        sender.setDefaultEncoding("UTF-8");
        sender.setProtocol("smtp");

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        if (config.port == 465) {
            // 465 端口：SMTPS 隐式 SSL
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.port));
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            // 587 等端口：STARTTLS
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
        }
        sender.setJavaMailProperties(props);
        return sender;
    }

    /** 兜底发件人：取 Spring 注入的 sender 的 username */
    private String resolveDefaultFrom(JavaMailSender sender) {
        if (sender instanceof JavaMailSenderImpl impl) {
            return impl.getUsername();
        }
        return null;
    }

    private int parsePort(String value) {
        if (StrUtil.isBlank(value)) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /** 拼装异常原因，包含嵌套异常信息 */
    private String rootMessage(MessagingException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage() == null ? e.toString() : e.getMessage());
        if (e.getCause() != null && e.getCause() != e) {
            sb.append(" -> ").append(e.getCause().getMessage());
        }
        return sb.toString();
    }

    /** SMTP 配置载体 */
    private static class MailConfig {
        private String host;
        private int port = DEFAULT_PORT;
        private String username;
        private String password;
        private String fromName;

        /** 是否具备完整配置（host + username + password） */
        private boolean complete() {
            return StrUtil.isNotBlank(host) && StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password);
        }
    }
}
