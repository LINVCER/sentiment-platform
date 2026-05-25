package com.sentiment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("health_check")
public class HealthCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleName;
    private String status;
    private LocalDateTime lastSuccess;
    private LocalDateTime lastHeartbeat;
    private String errorMessage;
    private String metadata;
}
