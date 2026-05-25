package com.sentiment.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentiment.entity.Alert;
import com.sentiment.entity.AlertRule;
import com.sentiment.entity.HotTopic;
import com.sentiment.entity.Post;
import com.sentiment.mapper.AlertMapper;
import com.sentiment.mapper.AlertRuleMapper;
import com.sentiment.mapper.HotTopicMapper;
import com.sentiment.mapper.PostMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private HotTopicMapper hotTopicMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 60000) // Check every minute
    public void checkAlerts() {
        try {
            List<AlertRule> rules = alertRuleMapper.selectEnabled();
            for (AlertRule rule : rules) {
                switch (rule.getRuleType()) {
                    case "negative_surge":
                        checkNegativeSurge(rule);
                        break;
                    case "keyword_trigger":
                        checkKeywordTrigger(rule);
                        break;
                    case "topic_growth":
                        checkTopicGrowth(rule);
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Alert engine error", e);
        }
    }

    private void checkNegativeSurge(AlertRule rule) throws Exception {
        Map<String, Object> config = objectMapper.readValue(rule.getConfig(), Map.class);
        double threshold = ((Number) config.get("negativeRatioThreshold")).doubleValue();
        int minPosts = ((Number) config.get("minPostCount")).intValue();
        int windowHours = ((Number) config.get("windowHours")).intValue();
        int silenceHours = ((Number) config.get("silenceHours")).intValue();

        String startTime = LocalDateTime.now().minusHours(windowHours)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        long totalPosts = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .ge(Post::getCrawlTime, startTime)
                        .isNotNull(Post::getSentiment));
        long negativePosts = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .ge(Post::getCrawlTime, startTime)
                        .eq(Post::getSentiment, 0));

        if (totalPosts < minPosts) return;

        double negativeRatio = (double) negativePosts / totalPosts;
        if (negativeRatio >= threshold) {
            if (!isInSilencePeriod("negative_surge", null, silenceHours)) {
                Map<String, Object> triggerData = new LinkedHashMap<>();
                triggerData.put("totalPosts", totalPosts);
                triggerData.put("negativePosts", negativePosts);
                triggerData.put("negativeRatio", Math.round(negativeRatio * 1000) / 10.0);
                triggerData.put("threshold", threshold * 100);
                triggerData.put("windowHours", windowHours);

                Alert alert = new Alert();
                alert.setAlertType("negative_surge");
                alert.setSeverity(negativeRatio >= 0.8 ? "critical" : "warning");
                alert.setTitle("负面舆情突增预警");
                alert.setDescription(String.format(
                        "近%d小时负面占比 %.1f%% (%d/%d条), 超过阈值 %.0f%%",
                        windowHours, negativeRatio * 100, negativePosts, totalPosts, threshold * 100));
                alert.setTriggerData(objectMapper.writeValueAsString(triggerData));
                alert.setIsRead(false);
                alert.setNotified(false);
                alertMapper.insert(alert);

                messagingTemplate.convertAndSend("/topic/alert", alert);
                notificationService.notify(alert);
                log.warn("Alert triggered: {}", alert.getTitle());
            }
        }
    }

    private void checkKeywordTrigger(AlertRule rule) throws Exception {
        Map<String, Object> config = objectMapper.readValue(rule.getConfig(), Map.class);
        List<String> sensitiveWords = (List<String>) config.get("sensitiveWords");
        int silenceHours = ((Number) config.get("silenceHours")).intValue();

        String startTime = LocalDateTime.now().minusMinutes(5)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Post> recentPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>().ge(Post::getCrawlTime, startTime));

        for (Post post : recentPosts) {
            for (String word : sensitiveWords) {
                if (post.getContent().contains(word)) {
                    if (!isInSilencePeriod("keyword_trigger", null, silenceHours)) {
                        Map<String, Object> triggerData = new LinkedHashMap<>();
                        triggerData.put("postId", post.getId());
                        triggerData.put("platform", post.getPlatform());
                        triggerData.put("matchedWord", word);
                        triggerData.put("contentPreview", post.getContent().substring(0, Math.min(80, post.getContent().length())));

                        Alert alert = new Alert();
                        alert.setAlertType("keyword_trigger");
                        alert.setSeverity("warning");
                        alert.setTitle("敏感词触发: " + word);
                        alert.setDescription("帖子内容包含敏感词「" + word + "」: " +
                                post.getContent().substring(0, Math.min(100, post.getContent().length())));
                        alert.setTriggerData(objectMapper.writeValueAsString(triggerData));
                        alert.setIsRead(false);
                        alert.setNotified(false);
                        alertMapper.insert(alert);

                        messagingTemplate.convertAndSend("/topic/alert", alert);
                        notificationService.notify(alert);
                    }
                    break; // One alert per post
                }
            }
        }
    }

    private void checkTopicGrowth(AlertRule rule) throws Exception {
        Map<String, Object> config = objectMapper.readValue(rule.getConfig(), Map.class);
        double growthRate = ((Number) config.get("growthRateThreshold")).doubleValue();
        int windowHours = ((Number) config.get("windowHours")).intValue();
        int silenceHours = ((Number) config.get("silenceHours")).intValue();

        String cutoffTime = LocalDateTime.now().minusHours(windowHours)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Get active topics that were created before the window (need history to compare)
        List<HotTopic> topics = hotTopicMapper.selectList(
                new LambdaQueryWrapper<HotTopic>()
                        .eq(HotTopic::getStatus, "active")
                        .ge(HotTopic::getLastUpdated, cutoffTime));

        for (HotTopic topic : topics) {
            if (topic.getFirstSeen() == null) continue;

            // Count posts in current window vs previous window
            long currentCount = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getTopicId, topic.getId())
                            .ge(Post::getCrawlTime, cutoffTime));
            long previousCount = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getTopicId, topic.getId())
                            .lt(Post::getCrawlTime, cutoffTime));

            if (previousCount < 5) continue; // Need minimum baseline

            double actualGrowth = (double) currentCount / previousCount;
            if (actualGrowth >= (1 + growthRate)) {
                if (!isInSilencePeriod("topic_growth", topic.getId(), silenceHours)) {
                    Map<String, Object> triggerData = new LinkedHashMap<>();
                    triggerData.put("topicId", topic.getId());
                    triggerData.put("topicName", topic.getTopicName());
                    triggerData.put("currentCount", currentCount);
                    triggerData.put("previousCount", previousCount);
                    triggerData.put("growthRate", Math.round(actualGrowth * 100) / 100.0);

                    String severity = actualGrowth >= (1 + growthRate * 2) ? "critical" : "warning";

                    Alert alert = new Alert();
                    alert.setAlertType("topic_growth");
                    alert.setSeverity(severity);
                    alert.setTitle("话题异常增长: " + topic.getTopicName());
                    alert.setDescription(String.format(
                            "话题「%s」在%d小时内帖子数增长 %.0f%% (%d→%d), 超过阈值 %.0f%%",
                            topic.getTopicName(), windowHours, (actualGrowth - 1) * 100,
                            previousCount, currentCount, growthRate * 100));
                    alert.setRelatedTopicId(topic.getId());
                    alert.setTriggerData(objectMapper.writeValueAsString(triggerData));
                    alert.setIsRead(false);
                    alert.setNotified(false);
                    alertMapper.insert(alert);

                    messagingTemplate.convertAndSend("/topic/alert", alert);
                    notificationService.notify(alert);
                    log.warn("Alert triggered: {}", alert.getTitle());
                }
            }
        }
    }

    private boolean isInSilencePeriod(String alertType, Long topicId, int silenceHours) {
        int count = alertMapper.countRecentByTypeAndTopic(alertType, topicId, silenceHours);
        return count > 0;
    }
}
