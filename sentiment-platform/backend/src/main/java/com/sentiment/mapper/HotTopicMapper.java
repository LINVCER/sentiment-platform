package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.HotTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface HotTopicMapper extends BaseMapper<HotTopic> {

    @Select("SELECT * FROM hot_topics WHERE status = 'active' ORDER BY heat_score DESC LIMIT #{limit}")
    List<HotTopic> selectTopTopics(@Param("limit") int limit);
}
