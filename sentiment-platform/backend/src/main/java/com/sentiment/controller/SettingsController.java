package com.sentiment.controller;

import com.sentiment.collector.XhsScraper;
import com.sentiment.entity.MonitorKeyword;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.MonitorKeywordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final String COLLECTOR_ENABLED_KEY = "sentiment:collector:enabled";
    private static final String ANALYZER_ENABLED_KEY = "sentiment:analyzer:enabled";

    @Autowired
    private MonitorKeywordMapper keywordMapper;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private XhsScraper xhsScraper;

    // ==================== Keywords ====================

    @GetMapping("/keywords")
    public List<MonitorKeyword> listKeywords() {
        return keywordMapper.selectList(null);
    }

    @PostMapping("/keywords")
    public MonitorKeyword addKeyword(@RequestBody MonitorKeyword keyword) {
        keywordMapper.insert(keyword);
        return keyword;
    }

    @PutMapping("/keywords/{id}")
    public void updateKeyword(@PathVariable Long id, @RequestBody MonitorKeyword keyword) {
        keyword.setId(id);
        keywordMapper.updateById(keyword);
    }

    @DeleteMapping("/keywords/{id}")
    public Map<String, Object> deleteKeyword(@PathVariable Long id) {
        keywordMapper.deleteById(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", id);
        return resp;
    }

    // ==================== Health ====================

    @GetMapping("/health")
    public Map<String, Object> systemHealth() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("modules", healthCheckService.getAllStatus());
        resp.put("collectorEnabled", isCollectorEnabled());
        resp.put("analyzerEnabled", isAnalyzerEnabled());
        return resp;
    }

    // ==================== Collector Control ====================

    @PutMapping("/collector/enable")
    public Map<String, Object> enableCollector() {
        redisTemplate.opsForValue().set(COLLECTOR_ENABLED_KEY, "true");
        Map<String, Object> resp = new HashMap<>();
        resp.put("collectorEnabled", true);
        return resp;
    }

    @PutMapping("/collector/disable")
    public Map<String, Object> disableCollector() {
        redisTemplate.opsForValue().set(COLLECTOR_ENABLED_KEY, "false");
        Map<String, Object> resp = new HashMap<>();
        resp.put("collectorEnabled", false);
        return resp;
    }

    // ==================== Analyzer Control ====================

    @PutMapping("/analyzer/enable")
    public Map<String, Object> enableAnalyzer() {
        redisTemplate.opsForValue().set(ANALYZER_ENABLED_KEY, "true");
        Map<String, Object> resp = new HashMap<>();
        resp.put("analyzerEnabled", true);
        return resp;
    }

    @PutMapping("/analyzer/disable")
    public Map<String, Object> disableAnalyzer() {
        redisTemplate.opsForValue().set(ANALYZER_ENABLED_KEY, "false");
        Map<String, Object> resp = new HashMap<>();
        resp.put("analyzerEnabled", false);
        return resp;
    }

    // ==================== XHS Login ====================

    @PostMapping("/xhs/login")
    public Map<String, Object> xhsLogin() {
        Map<String, Object> resp = new HashMap<>();
        boolean success = xhsScraper.login();
        resp.put("success", success);
        resp.put("hasCookies", xhsScraper.hasCookies());
        return resp;
    }

    @GetMapping("/xhs/status")
    public Map<String, Object> xhsStatus() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("hasCookies", xhsScraper.hasCookies());
        return resp;
    }

    // ==================== Helper ====================

    public boolean isCollectorEnabled() {
        String val = redisTemplate.opsForValue().get(COLLECTOR_ENABLED_KEY);
        return val == null || "true".equals(val); // Default enabled
    }

    public boolean isAnalyzerEnabled() {
        String val = redisTemplate.opsForValue().get(ANALYZER_ENABLED_KEY);
        return val == null || "true".equals(val); // Default enabled
    }
}
