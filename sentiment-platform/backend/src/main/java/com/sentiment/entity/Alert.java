package com.sentiment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alerts")
public class Alert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private Long relatedTopicId;
    private String triggerData;
    private Boolean isRead;
    private Boolean notified;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
