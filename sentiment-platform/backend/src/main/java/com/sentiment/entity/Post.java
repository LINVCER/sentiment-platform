package com.sentiment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("posts")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String postId;
    private String author;
    private String content;
    private LocalDateTime publishTime;
    private Integer likes;
    private Integer comments;
    private Integer shares;
    private String url;
    private String province;
    private String city;
    private Integer sentiment;
    private Float sentimentScore;
    private Long topicId;
    private String keywords;
    private Integer analyzeStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime crawlTime;
}
