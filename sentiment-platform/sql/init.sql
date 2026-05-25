-- Sentiment Analysis Platform - Database Schema
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS sentiment_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE sentiment_db;

-- Posts table (merged with sentiment results)
CREATE TABLE IF NOT EXISTS posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(16) NOT NULL COMMENT 'weibo | xiaohongshu',
    post_id VARCHAR(64) NOT NULL COMMENT 'Platform original ID',
    author VARCHAR(128),
    content TEXT NOT NULL,
    publish_time DATETIME NOT NULL,
    likes INT DEFAULT 0,
    comments INT DEFAULT 0,
    shares INT DEFAULT 0,
    url VARCHAR(512),
    province VARCHAR(32),
    city VARCHAR(32),
    -- Sentiment results (filled after analysis)
    sentiment TINYINT COMMENT '0=negative 1=positive NULL=unanalyzed',
    sentiment_score FLOAT COMMENT 'Confidence 0~1',
    topic_id BIGINT COMMENT 'Associated topic ID',
    keywords VARCHAR(512) COMMENT 'Extracted keywords (comma separated)',
    -- Metadata
    analyze_status TINYINT DEFAULT 0 COMMENT '0=pending 1=done 2=failed',
    crawl_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_post (platform, post_id),
    INDEX idx_publish_time (publish_time),
    INDEX idx_platform (platform),
    INDEX idx_sentiment (sentiment),
    INDEX idx_analyze_status (analyze_status),
    INDEX idx_topic_id (topic_id),
    INDEX idx_province (province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Hot topics table
CREATE TABLE IF NOT EXISTS hot_topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_name VARCHAR(128) NOT NULL,
    keywords VARCHAR(512) COMMENT 'Core keywords',
    post_count INT DEFAULT 0,
    sentiment_ratio FLOAT COMMENT 'Positive ratio',
    heat_score FLOAT DEFAULT 0 COMMENT 'Heat score (posts + engagement + time decay)',
    sentiment_trend JSON COMMENT 'Time series sentiment trend',
    cluster_centroid BLOB COMMENT 'DBSCAN cluster centroid vector',
    first_seen DATETIME,
    last_updated DATETIME,
    status VARCHAR(16) DEFAULT 'active' COMMENT 'active/cold/archived',
    INDEX idx_status (status),
    INDEX idx_heat_score (heat_score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(32) NOT NULL COMMENT 'negative_surge|hot_topic|keyword_trigger',
    severity VARCHAR(16) NOT NULL COMMENT 'info|warning|critical',
    title VARCHAR(256) NOT NULL,
    description TEXT,
    related_topic_id BIGINT,
    trigger_data JSON COMMENT 'Snapshot data at trigger time',
    is_read BOOLEAN DEFAULT FALSE,
    notified BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at),
    INDEX idx_severity (severity),
    INDEX idx_type_topic (alert_type, related_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Health check table
CREATE TABLE IF NOT EXISTS health_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module_name VARCHAR(32) NOT NULL COMMENT 'weibo_scraper|xhs_scraper|analyzer|alerter',
    status VARCHAR(16) NOT NULL COMMENT 'healthy|degraded|down',
    last_success DATETIME,
    last_heartbeat DATETIME,
    error_message TEXT,
    metadata JSON COMMENT 'Queue backlog, last crawl count, etc.',
    UNIQUE KEY uk_module (module_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Monitor keywords config
CREATE TABLE IF NOT EXISTS monitor_keywords (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(64) NOT NULL,
    platform VARCHAR(16) DEFAULT 'all' COMMENT 'weibo|xiaohongshu|all',
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_keyword_platform (keyword, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Alert rules config
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(64) NOT NULL,
    rule_type VARCHAR(32) NOT NULL COMMENT 'negative_surge|keyword_trigger|topic_growth',
    config JSON NOT NULL COMMENT 'Rule parameters (thresholds, window, silence period)',
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Default alert rules
INSERT INTO alert_rules (rule_name, rule_type, config) VALUES
('Negative Surge', 'negative_surge',
 '{"negativeRatioThreshold": 0.6, "minPostCount": 20, "windowHours": 1, "baselineHours": 6, "baselineThreshold": 0.3, "silenceHours": 24}'),
('Keyword Trigger', 'keyword_trigger',
 '{"sensitiveWords": ["暴雷", "跑路", "诈骗", "维权"], "silenceHours": 1}'),
('Topic Growth', 'topic_growth',
 '{"growthRateThreshold": 3.0, "windowHours": 24, "silenceHours": 48}');

-- Default monitor keywords
INSERT INTO monitor_keywords (keyword, platform) VALUES
('AI', 'all'),
('新能源', 'all'),
('就业', 'all'),
('人工智能', 'all');
