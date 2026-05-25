package com.sentiment.analyzer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentiment.entity.HotTopic;
import com.sentiment.entity.Post;
import com.sentiment.health.HealthCheckService;
import com.sentiment.mapper.HotTopicMapper;
import com.sentiment.mapper.PostMapper;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Topic clustering using TF-IDF vectorization + DBSCAN.
 *
 * Each post is converted to a sparse TF-IDF vector, then DBSCAN groups
 * semantically similar posts into clusters. Each cluster becomes a hot topic.
 *
 * For production with higher accuracy needs, replace TF-IDF with a sentence
 * embedding model (text2vec-base-chinese via ONNX Runtime or HTTP API).
 */
@Component
public class TopicClusterer {

    private static final Logger log = LoggerFactory.getLogger(TopicClusterer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private HotTopicMapper hotTopicMapper;

    @Autowired
    private HealthCheckService healthCheckService;

    @Value("${sentiment.topic.eps:0.7}")
    private double eps; // Cosine distance threshold (1 - similarity)

    @Value("${sentiment.topic.min-samples:3}")
    private int minSamples;

    @Value("${sentiment.topic.similarity-threshold:0.85}")
    private double mergeSimilarity;

    @Scheduled(fixedDelayString = "${sentiment.topic.cluster-interval-ms:3600000}")
    public void cluster() {
        log.info("Starting topic clustering...");
        try {
            // 1. Get recent analyzed posts (last 24h)
            String startTime = LocalDateTime.now().minusHours(24)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            List<Post> posts = postMapper.selectList(
                    new LambdaQueryWrapper<Post>()
                            .ge(Post::getCrawlTime, startTime)
                            .eq(Post::getAnalyzeStatus, 1)
                            .isNotNull(Post::getSentiment));

            if (posts.size() < minSamples) {
                log.info("Only {} analyzed posts, skipping clustering", posts.size());
                healthCheckService.heartbeat("topic_clusterer", "healthy",
                        "{\"posts\":" + posts.size() + ",\"skipped\":true}");
                return;
            }

            log.info("Clustering {} posts", posts.size());

            // 2. Build vocabulary and TF-IDF vectors
            List<List<String>> tokenizedPosts = posts.stream()
                    .map(p -> tokenize(p.getContent()))
                    .collect(Collectors.toList());
            Map<String, Integer> vocabulary = buildVocabulary(tokenizedPosts);
            List<Map<Integer, Double>> tfidfVectors = computeTfidf(tokenizedPosts, vocabulary);

            // 3. DBSCAN clustering
            int[] labels = dbscan(tfidfVectors, vocabulary.size());

            // 4. Group posts by cluster
            Map<Integer, List<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < labels.length; i++) {
                clusters.computeIfAbsent(labels[i], k -> new ArrayList<>()).add(i);
            }

            int noiseCount = clusters.containsKey(-1) ? clusters.get(-1).size() : 0;
            int clusterCount = clusters.size() - (clusters.containsKey(-1) ? 1 : 0);
            log.info("DBSCAN found {} clusters, {} noise points", clusterCount, noiseCount);

            // 5. Create/update topics from clusters
            int topicsCreated = 0;
            for (Map.Entry<Integer, List<Integer>> entry : clusters.entrySet()) {
                int clusterId = entry.getKey();
                if (clusterId == -1) continue; // Skip noise

                List<Integer> memberIndices = entry.getValue();
                if (memberIndices.size() < minSamples) continue;

                List<Post> memberPosts = memberIndices.stream()
                        .map(posts::get)
                        .collect(Collectors.toList());

                // Extract topic name from cluster keywords
                String topicName = extractTopicName(memberPosts);
                String keywords = extractKeywords(memberPosts, 8);

                // Calculate sentiment ratio
                long positiveCount = memberPosts.stream()
                        .filter(p -> p.getSentiment() != null && p.getSentiment() == 1)
                        .count();
                float sentimentRatio = (float) positiveCount / memberPosts.size();

                // Calculate heat score: post_count * avg_engagement * time_factor
                double avgLikes = memberPosts.stream()
                        .mapToInt(p -> p.getLikes() != null ? p.getLikes() : 0)
                        .average().orElse(0);
                double timeFactor = 1.0; // Could add time decay
                float heatScore = (float) (memberPosts.size() * (1 + avgLikes / 100.0) * timeFactor);

                // Check if similar topic already exists
                HotTopic existingTopic = findSimilarTopic(topicName, keywords);
                if (existingTopic != null) {
                    // Merge into existing topic
                    existingTopic.setPostCount(existingTopic.getPostCount() + memberPosts.size());
                    existingTopic.setHeatScore(Math.max(existingTopic.getHeatScore(), heatScore));
                    existingTopic.setSentimentRatio(
                            (existingTopic.getSentimentRatio() * existingTopic.getPostCount()
                                    + sentimentRatio * memberPosts.size())
                                    / (existingTopic.getPostCount() + memberPosts.size()));
                    existingTopic.setLastUpdated(LocalDateTime.now());
                    updateSentimentTrend(existingTopic, sentimentRatio);
                    hotTopicMapper.updateById(existingTopic);

                    // Link posts to existing topic
                    for (Post p : memberPosts) {
                        p.setTopicId(existingTopic.getId());
                        postMapper.updateById(p);
                    }
                } else {
                    // Create new topic
                    HotTopic topic = new HotTopic();
                    topic.setTopicName(topicName);
                    topic.setKeywords(keywords);
                    topic.setPostCount(memberPosts.size());
                    topic.setSentimentRatio(sentimentRatio);
                    topic.setHeatScore(heatScore);
                    topic.setFirstSeen(LocalDateTime.now());
                    topic.setLastUpdated(LocalDateTime.now());
                    topic.setStatus("active");
                    updateSentimentTrend(topic, sentimentRatio);
                    hotTopicMapper.insert(topic);

                    // Link posts to new topic
                    for (Post p : memberPosts) {
                        p.setTopicId(topic.getId());
                        postMapper.updateById(p);
                    }
                    topicsCreated++;
                }
            }

            // 6. Cool down old topics
            coolDownOldTopics();

            log.info("Topic clustering complete: {} new topics created, {} total clusters",
                    topicsCreated, clusterCount);
            healthCheckService.heartbeat("topic_clusterer", "healthy",
                    "{\"posts\":" + posts.size() + ",\"clusters\":" + clusterCount
                            + ",\"created\":" + topicsCreated + "}");

        } catch (Exception e) {
            log.error("Topic clustering failed", e);
            healthCheckService.heartbeat("topic_clusterer", "degraded",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ==================== Tokenization ====================

    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<Term> terms = StandardTokenizer.segment(text);
        Set<String> stopWords = Set.of(
                "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
                "都", "一", "上", "也", "很", "到", "说", "要", "去", "你",
                "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
                "们", "那", "被", "从", "对", "把", "与", "以", "及",
                "等", "但", "而", "或", "又", "如", "所", "之", "其"
        );
        return terms.stream()
                .map(t -> t.word.toLowerCase().trim())
                .filter(w -> w.length() >= 2 && !stopWords.contains(w))
                .collect(Collectors.toList());
    }

    // ==================== TF-IDF Vectorization ====================

    private Map<String, Integer> buildVocabulary(List<List<String>> docs) {
        Map<String, Integer> docFreq = new HashMap<>();
        for (List<String> doc : docs) {
            new HashSet<>(doc).forEach(word -> docFreq.merge(word, 1, Integer::sum));
        }
        // Keep words that appear in at least 2 documents, up to 5000
        Map<String, Integer> vocab = new HashMap<>();
        int idx = 0;
        for (Map.Entry<String, Integer> e : docFreq.entrySet()) {
            if (e.getValue() >= 2 && idx < 5000) {
                vocab.put(e.getKey(), idx++);
            }
        }
        return vocab;
    }

    private List<Map<Integer, Double>> computeTfidf(List<List<String>> docs, Map<String, Integer> vocab) {
        int n = docs.size();
        List<Map<Integer, Double>> vectors = new ArrayList<>();

        // Compute IDF
        Map<String, Integer> docFreq = new HashMap<>();
        for (List<String> doc : docs) {
            new HashSet<>(doc).forEach(w -> {
                if (vocab.containsKey(w)) docFreq.merge(w, 1, Integer::sum);
            });
        }

        for (List<String> doc : docs) {
            // TF
            Map<String, Integer> tf = new HashMap<>();
            doc.forEach(w -> tf.merge(w, 1, Integer::sum));

            // TF-IDF vector (sparse)
            Map<Integer, Double> vec = new HashMap<>();
            int maxTf = tf.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                Integer idx = vocab.get(e.getKey());
                if (idx == null) continue;
                double tfNorm = 0.5 + 0.5 * (double) e.getValue() / maxTf; // Augmented TF
                double idf = Math.log((double) n / (1 + docFreq.getOrDefault(e.getKey(), 0)));
                vec.put(idx, tfNorm * idf);
            }
            vectors.add(vec);
        }
        return vectors;
    }

    // ==================== DBSCAN ====================

    private int[] dbscan(List<Map<Integer, Double>> vectors, int dim) {
        int n = vectors.size();
        int[] labels = new int[n];
        Arrays.fill(labels, -1); // -1 = unvisited/noise
        int clusterId = 0;

        for (int i = 0; i < n; i++) {
            if (labels[i] != -1) continue; // Already visited

            List<Integer> neighbors = rangeQuery(vectors, i);
            if (neighbors.size() < minSamples) {
                labels[i] = -1; // Noise
                continue;
            }

            // Start new cluster
            labels[i] = clusterId;
            List<Integer> seeds = new ArrayList<>(neighbors);
            int seedIdx = 0;
            while (seedIdx < seeds.size()) {
                int j = seeds.get(seedIdx);
                if (labels[j] == -1) {
                    labels[j] = clusterId; // Was noise, now border point
                }
                if (labels[j] != -1) {
                    seedIdx++;
                    continue; // Already processed
                }
                labels[j] = clusterId;
                List<Integer> jNeighbors = rangeQuery(vectors, j);
                if (jNeighbors.size() >= minSamples) {
                    for (int k : jNeighbors) {
                        if (!seeds.contains(k)) seeds.add(k);
                    }
                }
                seedIdx++;
            }
            clusterId++;
        }
        return labels;
    }

    private List<Integer> rangeQuery(List<Map<Integer, Double>> vectors, int idx) {
        List<Integer> neighbors = new ArrayList<>();
        for (int j = 0; j < vectors.size(); j++) {
            if (cosineDistance(vectors.get(idx), vectors.get(j)) <= eps) {
                neighbors.add(j);
            }
        }
        return neighbors;
    }

    private double cosineDistance(Map<Integer, Double> a, Map<Integer, Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (Map.Entry<Integer, Double> e : a.entrySet()) {
            Double bv = b.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
            normA += e.getValue() * e.getValue();
        }
        for (Double v : b.values()) normB += v * v;
        if (normA == 0 || normB == 0) return 1.0;
        double similarity = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return 1.0 - similarity; // Distance = 1 - similarity
    }

    // ==================== Topic Extraction ====================

    private String extractTopicName(List<Post> posts) {
        Map<String, Integer> wordFreq = new HashMap<>();
        for (Post p : posts) {
            tokenize(p.getContent()).forEach(w -> wordFreq.merge(w, 1, Integer::sum));
        }
        // Top 3 words as topic name
        return wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("/"));
    }

    private String extractKeywords(List<Post> posts, int topN) {
        Map<String, Integer> wordFreq = new HashMap<>();
        for (Post p : posts) {
            tokenize(p.getContent()).forEach(w -> wordFreq.merge(w, 1, Integer::sum));
        }
        return wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }

    // ==================== Topic Merging ====================

    private HotTopic findSimilarTopic(String newName, String newKeywords) {
        List<HotTopic> existing = hotTopicMapper.selectList(
                new LambdaQueryWrapper<HotTopic>().eq(HotTopic::getStatus, "active"));
        Set<String> newKwSet = new HashSet<>(Arrays.asList(newKeywords.split(",")));

        for (HotTopic topic : existing) {
            if (topic.getKeywords() == null) continue;
            Set<String> existingKw = new HashSet<>(Arrays.asList(topic.getKeywords().split(",")));
            // Jaccard similarity of keyword sets
            Set<String> intersection = new HashSet<>(newKwSet);
            intersection.retainAll(existingKw);
            Set<String> union = new HashSet<>(newKwSet);
            union.addAll(existingKw);
            double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
            if (jaccard >= mergeSimilarity) {
                return topic;
            }
        }
        return null;
    }

    // ==================== Sentiment Trend ====================

    private void updateSentimentTrend(HotTopic topic, double sentimentRatio) {
        try {
            String hour = LocalDateTime.now().format(HOUR_FMT);
            Map<String, Double> trend;
            if (topic.getSentimentTrend() != null && !topic.getSentimentTrend().isEmpty()) {
                trend = objectMapper.readValue(topic.getSentimentTrend(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
            } else {
                trend = new LinkedHashMap<>();
            }
            trend.put(hour, sentimentRatio);
            // Keep only last 72 hours
            if (trend.size() > 72) {
                List<String> keys = new ArrayList<>(trend.keySet());
                for (int i = 0; i < keys.size() - 72; i++) {
                    trend.remove(keys.get(i));
                }
            }
            topic.setSentimentTrend(objectMapper.writeValueAsString(trend));
        } catch (Exception e) {
            log.debug("Failed to update sentiment trend", e);
        }
    }

    // ==================== Topic Lifecycle ====================

    private void coolDownOldTopics() {
        String cutoff = LocalDateTime.now().minusHours(48)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<HotTopic> oldTopics = hotTopicMapper.selectList(
                new LambdaQueryWrapper<HotTopic>()
                        .eq(HotTopic::getStatus, "active")
                        .lt(HotTopic::getLastUpdated, cutoff));
        for (HotTopic topic : oldTopics) {
            topic.setStatus("cold");
            hotTopicMapper.updateById(topic);
        }
        if (!oldTopics.isEmpty()) {
            log.info("Cooled down {} old topics", oldTopics.size());
        }
    }
}
