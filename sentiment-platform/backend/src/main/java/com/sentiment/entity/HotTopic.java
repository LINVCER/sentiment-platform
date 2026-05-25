package com.sentiment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("hot_topics")
public class HotTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String topicName;
    private String keywords;
    private Integer postCount;
    private Float sentimentRatio;
    private Float heatScore;
    private String sentimentTrend;
    private byte[] clusterCentroid;
    private LocalDateTime firstSeen;
    private LocalDateTime lastUpdated;
    private String status;
}
