package com.sentiment.analyzer;

import com.sentiment.controller.SettingsController;
import com.sentiment.entity.Post;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AnalysisConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisConsumer.class);

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private SentimentService sentimentService;

    @Autowired
    private KeywordExtractor keywordExtractor;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SettingsController settingsController;

    @Value("${sentiment.analysis.batch-size:64}")
    private int batchSize;

    @Value("${sentiment.analysis.min-batch-size:8}")
    private int minBatchSize;

    @Scheduled(fixedDelayString = "${sentiment.analysis.interval-seconds:30}000")
    public void consume() {
        if (!settingsController.isAnalyzerEnabled()) {
            log.debug("Analyzer disabled, skipping");
            return;
        }
        if (!sentimentService.isReady()) {
            return;
        }

        try {
            List<Post> posts = postMapper.selectUnanalyzed(batchSize);
            if (posts.size() < minBatchSize) {
                log.debug("Only {} pending posts (min={}), skipping batch", posts.size(), minBatchSize);
                healthCheckService.heartbeat("analyzer", "healthy",
                        "{\"pending\":" + posts.size() + ",\"skipped\":true}");
                return;
            }

            log.info("Analyzing batch of {} posts", posts.size());
            List<String> texts = posts.stream().map(Post::getContent).collect(Collectors.toList());
            List<float[]> results = sentimentService.batchPredict(texts);

            int successCount = 0;
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                float[] result = results.get(i);
                int sentiment = (int) result[0];
                float score = result[1];

                postMapper.updateSentiment(post.getId(), sentiment, score);

                try {
                    String keywords = keywordExtractor.extract(post.getContent(), 5);
                    post.setKeywords(keywords);
                    post.setSentiment(sentiment);
                    post.setSentimentScore(score);
                } catch (Exception e) {
                    log.warn("Keyword extraction failed for post {}", post.getId(), e);
                }

                successCount++;
                messagingTemplate.convertAndSend("/topic/new-post", post);
            }

            log.info("Batch analysis complete: {}/{} posts processed", successCount, posts.size());
            healthCheckService.heartbeat("analyzer", "healthy",
                    "{\"batchSize\":" + posts.size() + ",\"success\":" + successCount + "}");

        } catch (Exception e) {
            log.error("Analysis consumer error", e);
            healthCheckService.heartbeat("analyzer", "degraded", "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
