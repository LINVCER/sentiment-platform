package com.sentiment.collector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentiment.entity.Post;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.PostMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class XhsScraper {

    private static final Logger log = LoggerFactory.getLogger(XhsScraper.class);
    private static final String SEARCH_URL = "https://www.xiaohongshu.com/search_result?keyword=%s&source=web_search_result_notes";
    private static final String LOGIN_URL = "https://www.xiaohongshu.com";
    private static final String REDIS_COOKIE_KEY = "xhs:cookies";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36"
    };

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

    /**
     * Interactive login: opens browser for user to scan QR code.
     * Returns true if login succeeded (cookies saved).
     */
    public boolean login() {
        log.info("Opening XHS login page...");
        try (Playwright playwright = Playwright.create()) {
            // Must use headed mode for user to scan QR
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setSlowMo(300));

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(USER_AGENTS[0])
                            .setViewportSize(1440, 900)
                            .setLocale("zh-CN")
                            .setTimezoneId("Asia/Shanghai"));

            Page page = context.newPage();
            page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");

            page.navigate(LOGIN_URL, new Page.NavigateOptions().setTimeout(30000));

            log.info("请在浏览器中完成登录（扫码或账号密码），登录成功后脚本会自动检测...");

            // Wait for login to complete: check for user avatar or search box appearing
            // XHS redirects after login, or shows user info in header
            int maxWait = 120; // 2 minutes max
            for (int i = 0; i < maxWait; i++) {
                page.waitForTimeout(1000);

                // Check if logged in: look for user avatar, or search page accessible
                boolean loggedIn = page.locator(".user-avatar, .user-info, .side-bar .user, img.avatar").count() > 0;
                boolean noLoginWall = page.locator(".login-container, .qrcode-img").count() == 0;

                if (loggedIn || (noLoginWall && i > 10)) {
                    // Try navigating to search to verify
                    page.navigate("https://www.xiaohongshu.com/search_result?keyword=test",
                            new Page.NavigateOptions().setTimeout(15000));
                    page.waitForTimeout(2000);

                    if (page.locator(".login-container, .qrcode-img").count() == 0) {
                        log.info("XHS login successful! Saving cookies...");
                        saveCookies(context);
                        browser.close();
                        healthCheckService.heartbeat("xhs_scraper", "healthy",
                                "{\"action\":\"login\",\"result\":\"success\"}");
                        return true;
                    }
                }

                if (i % 10 == 0 && i > 0) {
                    log.info("等待登录... ({}/{}秒)", i, maxWait);
                }
            }

            log.warn("XHS login timeout ({}s)", maxWait);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("xhs_login_timeout.png")));
            browser.close();
            return false;

        } catch (Exception e) {
            log.error("XHS login failed", e);
            return false;
        }
    }

    /**
     * Check if valid cookies exist in Redis.
     */
    public boolean hasCookies() {
        String cookies = redisTemplate.opsForValue().get(REDIS_COOKIE_KEY);
        return cookies != null && !cookies.isEmpty() && cookies.length() > 10;
    }

    /**
     * Scrape Xiaohongshu notes for a given keyword.
     * Returns the number of new posts saved.
     */
    public int scrape(String keyword) {
        if (!hasCookies()) {
            log.warn("No XHS cookies available. Run login first.");
            healthCheckService.heartbeat("xhs_scraper", "degraded",
                    "{\"keyword\":\"" + keyword + "\",\"error\":\"no_cookies\"}");
            return 0;
        }

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

            log.info("Running XHS Scrapy crawler for keyword: '{}' in: {}", keyword, scrapyDir.getAbsolutePath());

            int countBefore = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getPlatform, "xiaohongshu")).intValue();

            ProcessBuilder pb = new ProcessBuilder(
                    "py", "-m", "scrapy", "crawl", "xhs", "-a", "keyword=" + keyword
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
                    log.debug("[Scrapy XHS] " + line);
                }
            }

            int exitCode = process.waitFor();
            log.info("XHS Scrapy process finished with exit code: " + exitCode);

            int countAfter = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getPlatform, "xiaohongshu")).intValue();

            savedCount = countAfter - countBefore;

            if (exitCode == 0) {
                healthCheckService.heartbeat("xhs_scraper", "healthy",
                        "{\"keyword\":\"" + keyword + "\",\"saved\":" + savedCount + "}");
            } else {
                healthCheckService.heartbeat("xhs_scraper", "degraded",
                        "{\"keyword\":\"" + keyword + "\",\"error\":\"process_exit_code_" + exitCode + "\"}");
            }

        } catch (Exception e) {
            log.error("XHS Scrapy execution failed for keyword: {}", keyword, e);
            healthCheckService.heartbeat("xhs_scraper", "degraded",
                    "{\"keyword\":\"" + keyword + "\",\"error\":\"" + e.getMessage() + "\"}");
        }
        return savedCount;
    }


    private String fetchNoteContent(Page mainPage, String noteUrl, String fallback) {
        try {
            Page notePage = mainPage.context().newPage();
            notePage.navigate(noteUrl, new Page.NavigateOptions().setTimeout(15000));
            notePage.waitForTimeout(2000);
            Locator contentEl = notePage.locator("#detail-desc, .desc, .note-text, .content");
            String content = contentEl.count() > 0 ? contentEl.first().innerText().trim() : fallback;
            notePage.close();
            return content;
        } catch (Exception e) {
            log.debug("Failed to fetch note detail: {}", noteUrl);
            return fallback;
        }
    }

    private String extractNoteId(String url) {
        if (url == null || url.isEmpty()) return String.valueOf(System.currentTimeMillis());
        String[] parts = url.split("[/?]");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("[a-f0-9]{24}")) return parts[i];
        }
        return String.valueOf(Math.abs(url.hashCode()));
    }

    private int parseCount(String str) {
        if (str == null || str.isEmpty()) return 0;
        str = str.trim();
        try {
            if (str.contains("万")) return (int) (Double.parseDouble(str.replace("万", "").trim()) * 10000);
            return Integer.parseInt(str.replaceAll("\\D+", ""));
        } catch (Exception e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private void loadCookies(BrowserContext context) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_COOKIE_KEY);
            if (json != null && !json.isEmpty()) {
                List<Cookie> cookies = objectMapper.readValue(json, new TypeReference<List<Cookie>>() {});
                context.addCookies(cookies);
                log.debug("Loaded {} XHS cookies from Redis", cookies.size());
            }
        } catch (Exception e) {
            log.debug("Failed to load XHS cookies", e);
        }
    }

    private void saveCookies(BrowserContext context) {
        try {
            List<Cookie> cookies = context.cookies();
            String json = objectMapper.writeValueAsString(cookies);
            redisTemplate.opsForValue().set(REDIS_COOKIE_KEY, json);
            log.debug("Saved {} XHS cookies to Redis", cookies.size());
        } catch (Exception e) {
            log.debug("Failed to save XHS cookies", e);
        }
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
