package com.sentiment.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentiment.entity.Alert;
import com.sentiment.mapper.AlertMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private AlertMapper alertMapper;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${notification.email-to:}")
    private String emailTo;

    @Value("${notification.webhook-url:}")
    private String webhookUrl;

    @Value("${notification.webhook-type:wechat}")
    private String webhookType;

    public void notify(Alert alert) {
        if ("info".equals(alert.getSeverity())) {
            log.info("Info alert (no notification): {}", alert.getTitle());
            return;
        }

        log.warn("[{}] {}: {}", alert.getSeverity().toUpperCase(), alert.getTitle(), alert.getDescription());

        boolean notified = false;

        // Send email for critical alerts
        if ("critical".equals(alert.getSeverity()) && !emailTo.isEmpty()) {
            notified = sendEmail(alert);
        }

        // Send webhook for warning/critical
        if (!webhookUrl.isEmpty()) {
            notified = sendWebhook(alert) || notified;
        }

        // Mark as notified
        if (notified) {
            alert.setNotified(true);
            alertMapper.updateById(alert);
        }
    }

    private boolean sendEmail(Alert alert) {
        if (mailSender == null || mailFrom.isEmpty()) {
            log.debug("Mail sender not configured, skipping email");
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(emailTo.split(","));
            message.setSubject("[舆情预警] " + alert.getTitle());
            message.setText(buildEmailBody(alert));
            mailSender.send(message);
            log.info("Email sent for alert: {}", alert.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Failed to send email for alert: {}", alert.getTitle(), e);
            return false;
        }
    }

    private String buildEmailBody(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("舆情分析平台预警通知\n");
        sb.append("========================\n\n");
        sb.append("级别: ").append(severityLabel(alert.getSeverity())).append("\n");
        sb.append("类型: ").append(typeLabel(alert.getAlertType())).append("\n");
        sb.append("标题: ").append(alert.getTitle()).append("\n");
        sb.append("详情: ").append(alert.getDescription()).append("\n");
        sb.append("时间: ").append(alert.getCreatedAt()).append("\n");
        sb.append("\n请及时处理。\n");
        return sb.toString();
    }

    private boolean sendWebhook(Alert alert) {
        try {
            String body;
            if ("dingtalk".equalsIgnoreCase(webhookType)) {
                body = buildDingTalkBody(alert);
            } else {
                body = buildWeChatBody(alert);
            }

            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Webhook sent for alert: {} (type={})", alert.getTitle(), webhookType);
                    return true;
                } else {
                    log.warn("Webhook failed with code {} for alert: {}",
                            response.code(), alert.getTitle());
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Webhook request failed for alert: {}", alert.getTitle(), e);
            return false;
        }
    }

    private String buildWeChatBody(Alert alert) {
        // Enterprise WeChat webhook markdown format
        String severityIcon = "critical".equals(alert.getSeverity()) ? "🔴" : "🟡";
        String content = String.format(
                "### %s 舆情预警\n" +
                "> 级别: **%s**\n" +
                "> 类型: %s\n" +
                "> 详情: %s\n" +
                "> 时间: %s",
                severityIcon,
                severityLabel(alert.getSeverity()),
                typeLabel(alert.getAlertType()),
                alert.getDescription(),
                alert.getCreatedAt()
        );
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            Map<String, String> markdown = new HashMap<>();
            markdown.put("content", content);
            body.put("markdown", markdown);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildDingTalkBody(Alert alert) {
        // DingTalk webhook markdown format
        String content = String.format(
                "### 舆情预警\n" +
                "- 级别: **%s**\n" +
                "- 类型: %s\n" +
                "- 详情: %s\n" +
                "- 时间: %s",
                severityLabel(alert.getSeverity()),
                typeLabel(alert.getAlertType()),
                alert.getDescription(),
                alert.getCreatedAt()
        );
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", "舆情预警");
            markdown.put("text", content);
            body.put("markdown", markdown);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String severityLabel(String severity) {
        switch (severity) {
            case "critical": return "严重";
            case "warning": return "警告";
            case "info": return "提示";
            default: return severity;
        }
    }

    private String typeLabel(String type) {
        switch (type) {
            case "negative_surge": return "负面舆情突增";
            case "keyword_trigger": return "敏感词触发";
            case "topic_growth": return "话题异常增长";
            default: return type;
        }
    }
}
