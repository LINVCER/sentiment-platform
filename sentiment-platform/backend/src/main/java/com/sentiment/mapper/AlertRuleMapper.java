package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    @Select("SELECT * FROM alert_rules WHERE enabled = TRUE")
    List<AlertRule> selectEnabled();
}
