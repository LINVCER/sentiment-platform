package com.sentiment.collector;

import com.sentiment.entity.Post;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.PostMapper;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class WeiboScraper {

    private static final Logger log = LoggerFactory.getLogger(WeiboScraper.class);
    private static final String SEARCH_URL = "https://s.weibo.com/weibo?q=%s&timescope=custom:%%3A-1h";
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Edge/125.0.0.0"
    };

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HealthCheckService healthCheckService;

    public int scrape(String keyword) {
        int savedCount = 0;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(USER_AGENTS[ThreadLocalRandom.current().nextInt(USER_AGENTS.length)])
                            .setViewportSize(1920, 1080));

            // Load cookies from Redis if available
            String cookies = redisTemplate.opsForValue().get("weibo:cookies");
            if (cookies != null) {
                // context.addCookies(objectMapper.readValue(cookies, List.class));
            }

            Page page = context.newPage();
            String url = String.format(SEARCH_URL, java.net.URLEncoder.encode(keyword, "UTF-8"));
            page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
            page.waitForTimeout(3000);

            // Parse search results
            List<Locator> cards = page.locator(".card-wrap .content").all();
            for (Locator card : cards) {
                try {
                    String text = card.locator(".txt").innerText().trim();
                    if (text.isEmpty()) continue;

                    String author = card.locator(".name").innerText().trim();
                    String timeStr = card.locator(".from a").first().innerText().trim();

                    Post post = new Post();
                    post.setPlatform("weibo");
                    post.setPostId("wb_" + System.currentTimeMillis() + "_" + text.hashCode());
                    post.setAuthor(author);
                    post.setContent(text);
                    post.setPublishTime(parseWeiboTime(timeStr));
                    post.setUrl(url);
                    post.setAnalyzeStatus(0);

                    // Check duplicate
                    Long existing = postMapper.selectCount(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                                    .eq(Post::getPlatform, "weibo")
                                    .eq(Post::getContent, text));
                    if (existing == 0) {
                        postMapper.insert(post);
                        savedCount++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse a weibo card", e);
                }
            }

            // Save cookies
            // redisTemplate.opsForValue().set("weibo:cookies", objectMapper.writeValueAsString(context.cookies()));

            browser.close();
            healthCheckService.heartbeat("weibo_scraper", "healthy",
                    "{\"keyword\":\"" + keyword + "\",\"saved\":" + savedCount + "}");

        } catch (Exception e) {
            log.error("Weibo scraping failed for keyword: {}", keyword, e);
            healthCheckService.heartbeat("weibo_scraper", "degraded",
                    "{\"keyword\":\"" + keyword + "\",\"error\":\"" + e.getMessage() + "\"}");
        }
        return savedCount;
    }

    private LocalDateTime parseWeiboTime(String timeStr) {
        try {
            if (timeStr.contains("分钟前")) {
                int mins = Integer.parseInt(timeStr.replaceAll("\\D+", ""));
                return LocalDateTime.now().minusMinutes(mins);
            } else if (timeStr.contains("小时前")) {
                int hours = Integer.parseInt(timeStr.replaceAll("\\D+", ""));
                return LocalDateTime.now().minusHours(hours);
            } else if (timeStr.contains("今天")) {
                return LocalDateTime.now().withHour(12).withMinute(0);
            } else {
                return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
