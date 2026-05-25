package com.sentiment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sentiment.entity.Post;
import com.sentiment.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostMapper postMapper;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer sentiment,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (platform != null) wrapper.eq(Post::getPlatform, platform);
        if (sentiment != null) wrapper.eq(Post::getSentiment, sentiment);
        if (keyword != null) wrapper.like(Post::getContent, keyword);
        if (startTime != null) wrapper.ge(Post::getPublishTime, startTime);
        if (endTime != null) wrapper.le(Post::getPublishTime, endTime);
        wrapper.orderByDesc(Post::getPublishTime);

        Page<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        resp.put("pages", result.getPages());
        resp.put("current", result.getCurrent());
        return resp;
    }

    @GetMapping("/{id}")
    public Post getById(@PathVariable Long id) {
        return postMapper.selectById(id);
    }
}
