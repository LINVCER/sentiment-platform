package com.sentiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sentiment.entity.HealthCheck;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthCheckMapper extends BaseMapper<HealthCheck> {
}
