package com.aip.platform.work.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 总结报告邮件发送请求体
 *
 * <p>对应前端 {@code POST /work/summary/mail} 的 body：
 * <pre>{summaryId, receivers:[], cc:[], subject}</pre>
 * receivers / cc 元素可以是邮箱地址，也可以是系统用户ID。</p>
 */
@Data
public class MailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总结ID */
    private Long summaryId;

    /** 收件人：邮箱地址或用户ID */
    private List<String> receivers;

    /** 抄送：邮箱地址或用户ID */
    private List<String> cc;

    /** 邮件主题，为空时自动取总结标题 */
    private String subject;
}
