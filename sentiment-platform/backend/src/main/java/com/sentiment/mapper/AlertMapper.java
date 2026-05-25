package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    @Select("SELECT COUNT(*) FROM alerts WHERE is_read = FALSE")
    int countUnread();

    @Select("SELECT COUNT(*) FROM alerts WHERE alert_type=#{type} AND related_topic_id=#{topicId} " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
    int countRecentByTypeAndTopic(@Param("type") String type, @Param("topicId") Long topicId,
                                   @Param("hours") int hours);
}
