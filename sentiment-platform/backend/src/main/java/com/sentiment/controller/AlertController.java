package com.sentiment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sentiment.entity.Alert;
import com.sentiment.entity.AlertRule;
import com.sentiment.mapper.AlertMapper;
import com.sentiment.mapper.AlertRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity) {

        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (severity != null) wrapper.eq(Alert::getSeverity, severity);
        wrapper.orderByDesc(Alert::getCreatedAt);

        Page<Alert> result = alertMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        return resp;
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", alertMapper.countUnread());
        return resp;
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        Alert alert = alertMapper.selectById(id);
        if (alert != null) {
            alert.setIsRead(true);
            alertMapper.updateById(alert);
        }
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead() {
        int updated = alertMapper.update(null,
                new LambdaUpdateWrapper<Alert>().eq(Alert::getIsRead, false).set(Alert::getIsRead, true));
        Map<String, Object> resp = new HashMap<>();
        resp.put("updated", updated);
        return resp;
    }

    // ==================== Alert Rules ====================

    @GetMapping("/rules")
    public List<AlertRule> getRules() {
        return alertRuleMapper.selectList(null);
    }

    @PostMapping("/rules")
    public AlertRule createRule(@RequestBody AlertRule rule) {
        alertRuleMapper.insert(rule);
        return rule;
    }

    @PutMapping("/rules/{id}")
    public void updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        alertRuleMapper.updateById(rule);
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> deleteRule(@PathVariable Long id) {
        alertRuleMapper.deleteById(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", id);
        return resp;
    }
}
