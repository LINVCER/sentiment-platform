package com.sentiment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/live")
public class LiveFeedController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Posts are pushed via WebSocket at /topic/new-post.
     * This endpoint allows manual trigger for testing.
     */
    @PostMapping("/test")
    public String testPush(@RequestBody String message) {
        messagingTemplate.convertAndSend("/topic/new-post", message);
        return "Pushed";
    }
}
