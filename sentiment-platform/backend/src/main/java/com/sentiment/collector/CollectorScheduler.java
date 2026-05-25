package com.sentiment.collector;

import com.sentiment.controller.SettingsController;
import com.sentiment.entity.MonitorKeyword;
import com.sentiment.mapper.MonitorKeywordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectorScheduler.class);

    @Autowired
    private WeiboScraper weiboScraper;

    @Autowired
    private XhsScraper xhsScraper;

    @Autowired
    private MonitorKeywordMapper keywordMapper;

    @Autowired
    private SettingsController settingsController;

    @Scheduled(fixedDelayString = "${sentiment.collector.weibo-interval-ms:300000}")
    public void collectWeibo() {
        if (!settingsController.isCollectorEnabled()) {
            log.debug("Collector disabled, skipping Weibo collection");
            return;
        }
        List<MonitorKeyword> keywords = keywordMapper.selectEnabled();
        int totalSaved = 0;
        for (MonitorKeyword kw : keywords) {
            if ("all".equals(kw.getPlatform()) || "weibo".equals(kw.getPlatform())) {
                try {
                    int saved = weiboScraper.scrape(kw.getKeyword());
                    totalSaved += saved;
                    log.info("Weibo scrape [{}]: {} new posts", kw.getKeyword(), saved);
                    Thread.sleep(3000 + (long) (Math.random() * 2000));
                } catch (Exception e) {
                    log.error("Weibo scrape failed for: {}", kw.getKeyword(), e);
                }
            }
        }
        log.info("Weibo collection round complete: {} total new posts", totalSaved);
    }

    @Scheduled(fixedDelayString = "${sentiment.collector.xhs-interval-ms:600000}")
    public void collectXhs() {
        if (!settingsController.isCollectorEnabled()) {
            log.debug("Collector disabled, skipping XHS collection");
            return;
        }
        List<MonitorKeyword> keywords = keywordMapper.selectEnabled();
        int totalSaved = 0;
        for (MonitorKeyword kw : keywords) {
            if ("all".equals(kw.getPlatform()) || "xiaohongshu".equals(kw.getPlatform())) {
                try {
                    int saved = xhsScraper.scrape(kw.getKeyword());
                    totalSaved += saved;
                    log.info("XHS scrape [{}]: {} new notes", kw.getKeyword(), saved);
                    Thread.sleep(5000 + (long) (Math.random() * 3000));
                } catch (Exception e) {
                    log.error("XHS scrape failed for: {}", kw.getKeyword(), e);
                }
            }
        }
        log.info("XHS collection round complete: {} total new notes", totalSaved);
    }
}
