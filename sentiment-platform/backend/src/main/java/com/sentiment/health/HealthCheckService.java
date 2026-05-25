package com.sentiment.health;

import com.sentiment.entity.HealthCheck;
import com.sentiment.mapper.HealthCheckMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HealthCheckService {

    @Autowired
    private HealthCheckMapper healthCheckMapper;

    public void heartbeat(String moduleName, String status, String metadata) {
        HealthCheck hc = healthCheckMapper.selectOne(
                new LambdaQueryWrapper<HealthCheck>().eq(HealthCheck::getModuleName, moduleName));
        if (hc == null) {
            hc = new HealthCheck();
            hc.setModuleName(moduleName);
            hc.setStatus(status);
            hc.setLastSuccess(LocalDateTime.now());
            hc.setLastHeartbeat(LocalDateTime.now());
            hc.setMetadata(metadata);
            healthCheckMapper.insert(hc);
        } else {
            hc.setStatus(status);
            hc.setLastHeartbeat(LocalDateTime.now());
            if ("healthy".equals(status)) {
                hc.setLastSuccess(LocalDateTime.now());
                hc.setErrorMessage(null);
            }
            hc.setMetadata(metadata);
            healthCheckMapper.updateById(hc);
        }
    }

    public void recordError(String moduleName, String error) {
        HealthCheck hc = healthCheckMapper.selectOne(
                new LambdaQueryWrapper<HealthCheck>().eq(HealthCheck::getModuleName, moduleName));
        if (hc == null) {
            hc = new HealthCheck();
            hc.setModuleName(moduleName);
            hc.setStatus("down");
            hc.setErrorMessage(error);
            hc.setLastHeartbeat(LocalDateTime.now());
            healthCheckMapper.insert(hc);
        } else {
            hc.setStatus("down");
            hc.setErrorMessage(error);
            hc.setLastHeartbeat(LocalDateTime.now());
            healthCheckMapper.updateById(hc);
        }
    }

    public List<HealthCheck> getAllStatus() {
        return healthCheckMapper.selectList(null);
    }
}
