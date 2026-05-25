package com.sentiment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sentiment.entity.HotTopic;
import com.sentiment.entity.Post;
import com.sentiment.mapper.HotTopicMapper;
import com.sentiment.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    @Autowired
    private HotTopicMapper hotTopicMapper;

    @Autowired
    private PostMapper postMapper;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<HotTopic> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(HotTopic::getStatus, status);
        wrapper.orderByDesc(HotTopic::getHeatScore);

        Page<HotTopic> result = hotTopicMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        return resp;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        HotTopic topic = hotTopicMapper.selectById(id);
        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>().eq(Post::getTopicId, id)
                        .orderByDesc(Post::getPublishTime).last("LIMIT 50"));

        Map<String, Object> resp = new HashMap<>();
        resp.put("topic", topic);
        resp.put("posts", posts);
        return resp;
    }
}
