package com.sentiment.collector;

import com.sentiment.entity.Post;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

@Component
public class WeiboScraper {

    private static final Logger log = LoggerFactory.getLogger(WeiboScraper.class);

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HealthCheckService healthCheckService;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public int scrape(String keyword) {
        int savedCount = 0;
        try {
            String[] dbInfo = parseJdbcUrl(dbUrl);
            String host = dbInfo[0];
            String port = dbInfo[1];
            String db = dbInfo[2];

            String userDir = System.getProperty("user.dir");
            File scrapyDir = new File(userDir, "sentiment-platform/scrapy_collector");
            if (!scrapyDir.exists()) {
                scrapyDir = new File(userDir, "scrapy_collector");
            }
            if (!scrapyDir.exists()) {
                scrapyDir = new File("D:/A01/sentiment-platform/scrapy_collector");
            }

            log.info("Running Weibo Scrapy crawler for keyword: '{}' in: {}", keyword, scrapyDir.getAbsolutePath());

            int countBefore = postMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                            .eq(Post::getPlatform, "weibo")).intValue();

            ProcessBuilder pb = new ProcessBuilder(
                    "py", "-m", "scrapy", "crawl", "weibo", "-a", "keyword=" + keyword
            );
            pb.directory(scrapyDir);

            Map<String, String> env = pb.environment();
            env.put("MYSQL_HOST", host);
            env.put("MYSQL_PORT", port);
            env.put("MYSQL_USER", dbUsername);
            env.put("MYSQL_PASSWORD", dbPassword);
            env.put("MYSQL_DATABASE", db);

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[Scrapy Weibo] " + line);
                }
            }

            int exitCode = process.waitFor();
            log.info("Weibo Scrapy process finished with exit code: " + exitCode);

            int countAfter = postMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                            .eq(Post::getPlatform, "weibo")).intValue();

            savedCount = countAfter - countBefore;

            if (exitCode == 0) {
                healthCheckService.heartbeat("weibo_scraper", "healthy",
                        "{\"keyword\":\"" + keyword + "\",\"saved\":" + savedCount + "}");
            } else {
                healthCheckService.heartbeat("weibo_scraper", "degraded",
                        "{\"keyword\":\"" + keyword + "\",\"error\":\"process_exit_code_" + exitCode + "\"}");
            }

        } catch (Exception e) {
            log.error("Weibo Scrapy execution failed for keyword: {}", keyword, e);
            healthCheckService.heartbeat("weibo_scraper", "degraded",
                    "{\"keyword\":\"" + keyword + "\",\"error\":\"" + e.getMessage() + "\"}");
        }
        return savedCount;
    }

    private String[] parseJdbcUrl(String url) {
        String host = "localhost";
        String port = "3306";
        String db = "sentiment_db";
        try {
            String clean = url.substring(url.indexOf("//") + 2);
            int slashIdx = clean.indexOf("/");
            String hostPort = clean.substring(0, slashIdx);
            db = clean.substring(slashIdx + 1);
            if (db.contains("?")) {
                db = db.substring(0, db.indexOf("?"));
            }
            if (hostPort.contains(":")) {
                String[] parts = hostPort.split(":");
                host = parts[0];
                port = parts[1];
            } else {
                host = hostPort;
            }
        } catch (Exception e) {
            log.warn("Failed to parse JDBC url: {}, using defaults", url);
        }
        return new String[]{host, port, db};
    }
}
