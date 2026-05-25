package com.sentiment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("monitor_keywords")
public class MonitorKeyword {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private String platform;
    private Boolean enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
