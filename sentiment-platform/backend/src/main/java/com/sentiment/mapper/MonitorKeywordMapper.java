package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.MonitorKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MonitorKeywordMapper extends BaseMapper<MonitorKeyword> {

    @Select("SELECT * FROM monitor_keywords WHERE enabled = TRUE")
    List<MonitorKeyword> selectEnabled();
}
