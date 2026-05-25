package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.Post;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Select("SELECT * FROM posts WHERE analyze_status = 0 ORDER BY id ASC LIMIT #{limit}")
    List<Post> selectUnanalyzed(@Param("limit") int limit);

    @Update("<script>" +
            "UPDATE posts SET sentiment=#{sentiment}, sentiment_score=#{score}, analyze_status=1 WHERE id=#{id}" +
            "</script>")
    int updateSentiment(@Param("id") Long id, @Param("sentiment") Integer sentiment,
                        @Param("score") Float score);

    @Select("SELECT DATE_FORMAT(publish_time, '%Y-%m-%d %H:00:00') AS hour, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN sentiment=1 THEN 1 ELSE 0 END) AS positive, " +
            "SUM(CASE WHEN sentiment=0 THEN 1 ELSE 0 END) AS negative " +
            "FROM posts WHERE publish_time >= #{startTime} AND sentiment IS NOT NULL " +
            "GROUP BY hour ORDER BY hour")
    List<Map<String, Object>> selectSentimentTrend(@Param("startTime") String startTime);

    @Select("SELECT province, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN sentiment=1 THEN 1 ELSE 0 END) AS positive " +
            "FROM posts WHERE province IS NOT NULL AND sentiment IS NOT NULL " +
            "AND publish_time >= #{startTime} " +
            "GROUP BY province")
    List<Map<String, Object>> selectRegionStats(@Param("startTime") String startTime);

    @Select("SELECT COUNT(*) FROM posts WHERE DATE(crawl_time) = CURDATE()")
    int countToday();

    @Select("SELECT COUNT(*) FROM posts WHERE analyze_status = 0")
    int countPending();
}
