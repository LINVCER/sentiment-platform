package com.sentiment.controller;

import com.sentiment.entity.Alert;
import com.sentiment.entity.HotTopic;
import com.sentiment.entity.HealthCheck;
import com.sentiment.mapper.*;
import com.sentiment.health.HealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private HotTopicMapper hotTopicMapper;

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private HealthCheckService healthCheckService;

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", postMapper.countToday());
        stats.put("pendingCount", postMapper.countPending());
        stats.put("unreadAlerts", alertMapper.countUnread());

        // Sentiment distribution (today)
        Long positive = postMapper.selectCount(
                new LambdaQueryWrapper<com.sentiment.entity.Post>()
                        .apply("DATE(crawl_time) = CURDATE()")
                        .eq(com.sentiment.entity.Post::getSentiment, 1));
        Long negative = postMapper.selectCount(
                new LambdaQueryWrapper<com.sentiment.entity.Post>()
                        .apply("DATE(crawl_time) = CURDATE()")
                        .eq(com.sentiment.entity.Post::getSentiment, 0));
        stats.put("positive", positive);
        stats.put("negative", negative);
        return stats;
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> getTrend(@RequestParam(defaultValue = "24") int hours) {
        String startTime = java.time.LocalDateTime.now().minusHours(hours)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return postMapper.selectSentimentTrend(startTime);
    }

    @GetMapping("/topics")
    public List<HotTopic> getTopTopics(@RequestParam(defaultValue = "10") int limit) {
        return hotTopicMapper.selectTopTopics(limit);
    }

    @GetMapping("/alerts/recent")
    public List<Alert> getRecentAlerts(@RequestParam(defaultValue = "10") int limit) {
        return alertMapper.selectList(
                new LambdaQueryWrapper<Alert>()
                        .orderByDesc(Alert::getCreatedAt)
                        .last("LIMIT " + limit));
    }

    @GetMapping("/health")
    public List<HealthCheck> getHealth() {
        return healthCheckService.getAllStatus();
    }
}
