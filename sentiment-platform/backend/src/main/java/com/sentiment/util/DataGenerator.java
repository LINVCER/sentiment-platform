package com.sentiment.util;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generate 10000 realistic test posts and load into sentiment_db.
 * Run: mvn exec:java -Dexec.mainClass="com.sentiment.util.DataGenerator"
 */
public class DataGenerator {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/sentiment_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456";

    static final String[] POSITIVE_TEMPLATES = {
            "今天%s真的太棒了，体验非常好！",
            "刚体验了%s，效果超出预期，强烈推荐给大家",
            "%s的发展速度真的很快，未来可期",
            "作为一个从业者，我觉得%s的前景非常光明",
            "最近关注了%s，发现进步很大，值得肯定",
            "%s的技术创新让人眼前一亮，期待更多突破",
            "朋友推荐了%s，用了一段时间感觉确实不错",
            "%s的服务态度很好，用户体验一流",
            "看到%s的最新成果，真的很振奋人心",
            "支持%s，希望越来越好",
            "这次%s的改进确实很大，点赞",
            "%s的性价比很高，值得入手",
            "刚入手了%s，做工精细，质量很好",
            "%s的新功能很实用，解决了我的痛点",
            "给%s点个赞，客服回复很及时",
            "体验了%s一周，整体感觉非常满意",
            "%s的口碑一直不错，这次亲身体验确实好",
            "感谢%s团队的努力，产品越来越好用了",
            "%s的设计很人性化，细节处理到位",
            "第一次用%s就被圈粉了，真的好用",
    };

    static final String[] NEGATIVE_TEMPLATES = {
            "%s的体验太差了，完全不值这个价",
            "对%s非常失望，和宣传的完全不一样",
            "%s的服务态度很差，问题迟迟不解决",
            "劝大家不要踩坑，%s存在严重问题",
            "%s的质量堪忧，用了没多久就出问题了",
            "投诉%s多次，始终没有得到满意的答复",
            "%s的价格虚高，性价比极低",
            "使用%s过程中遇到了很多bug，体验很差",
            "%s的售后形同虚设，完全不负责任",
            "曝光%s的问题，希望大家引以为戒",
            "%s的虚假宣传太严重了，实际差距很大",
            "后悔选择了%s，浪费了时间和金钱",
            "%s的用户隐私保护存在严重隐患",
            "再也不用%s了，这次体验让我彻底失望",
            "%s的竞争对手明显更好，不理解为什么还有人用",
            "%s的更新把好用的功能都改没了",
            "等了半天%s的客服，结果问题还是没解决",
            "%s的包装太简陋了，收到时已经有损坏",
            "对比了几家，%s是最差的选择",
            "%s的广告打得很响，实际产品很一般",
    };

    static final String[] SUBJECTS = {
            "AI", "人工智能", "新能源汽车", "ChatGPT", "大模型",
            "直播带货", "新能源", "半导体", "芯片", "5G",
            "短视频", "电商", "教育", "医疗", "金融",
            "房地产", "旅游", "餐饮", "健身", "美妆",
            "iPhone", "华为", "小米", "特斯拉", "比亚迪",
            "抖音", "小红书", "微博", "微信", "淘宝",
            "就业", "考研", "考公", "留学", "创业",
            "股市", "基金", "理财", "房价", "消费",
            "环保", "碳中和", "数字化", "元宇宙", "Web3",
            "食品安全", "医疗改革", "教育双减", "延迟退休", "三胎政策",
    };

    static final String[] PROVINCES = {
            "北京", "上海", "广东", "浙江", "江苏",
            "四川", "湖北", "湖南", "山东", "河南",
            "福建", "安徽", "重庆", "天津", "陕西",
            "辽宁", "云南", "广西", "贵州", "江西",
    };

    static final String[] AUTHORS = {
            "用户A", "小明", "科技达人", "数码博主", "财经观察",
            "美食家", "旅行者", "健身达人", "职场人", "学生党",
            "宝妈日记", "程序员", "设计师", "教师", "医生",
            "创业者", "投资者", "分析师", "记者", "自由职业",
    };

    static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        System.out.println("=== 数据生成器 ===");
        System.out.println("目标: 10000条测试数据");

        Random rand = new Random(42);
        List<PostData> posts = generatePosts(rand, 10000);

        System.out.println("生成完成，开始写入数据库...");
        writeToDB(posts);
        System.out.println("=== 完成! 共写入 " + posts.size() + " 条数据 ===");
    }

    static List<PostData> generatePosts(Random rand, int count) {
        List<PostData> posts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            PostData p = new PostData();

            // Platform: 60% weibo, 40% xiaohongshu
            p.platform = rand.nextDouble() < 0.6 ? "weibo" : "xiaohongshu";
            p.postId = p.platform + "_" + (100000 + i);

            // Author
            p.author = AUTHORS[rand.nextInt(AUTHORS.length)] + (rand.nextInt(100) + 1);

            // Content: 50% positive, 40% negative, 10% neutral/mixed
            double sentimentRoll = rand.nextDouble();
            String subject = SUBJECTS[rand.nextInt(SUBJECTS.length)];
            if (sentimentRoll < 0.5) {
                p.sentiment = 1;
                p.sentimentScore = 0.6f + rand.nextFloat() * 0.4f;
                p.content = String.format(POSITIVE_TEMPLATES[rand.nextInt(POSITIVE_TEMPLATES.length)], subject);
            } else if (sentimentRoll < 0.9) {
                p.sentiment = 0;
                p.sentimentScore = 0.6f + rand.nextFloat() * 0.4f;
                p.content = String.format(NEGATIVE_TEMPLATES[rand.nextInt(NEGATIVE_TEMPLATES.length)], subject);
            } else {
                // Neutral/mixed - some analyzed, some pending
                if (rand.nextDouble() < 0.7) {
                    p.sentiment = rand.nextBoolean() ? 1 : 0;
                    p.sentimentScore = 0.5f + rand.nextFloat() * 0.2f;
                } else {
                    p.sentiment = null;
                    p.sentimentScore = null;
                    p.analyzeStatus = 0; // pending
                }
                p.content = "关于" + subject + "，最近讨论很多，各说各有理，大家怎么看？";
            }

            if (p.analyzeStatus == null) {
                p.analyzeStatus = 1; // analyzed
            }

            // Time: spread over last 7 days, more recent = more posts
            int daysAgo = (int) (rand.nextDouble() * rand.nextDouble() * 7); // skewed toward recent
            int hoursAgo = rand.nextInt(24);
            int minsAgo = rand.nextInt(60);
            p.publishTime = now.minusDays(daysAgo).minusHours(hoursAgo).minusMinutes(minsAgo);
            p.crawlTime = p.publishTime.plusMinutes(rand.nextInt(30));

            // Engagement
            p.likes = (int) (Math.pow(rand.nextDouble(), 0.3) * 10000); // Power law distribution
            p.comments = (int) (p.likes * (0.05 + rand.nextDouble() * 0.2));
            p.shares = (int) (p.likes * (0.02 + rand.nextDouble() * 0.1));

            // Province
            p.province = PROVINCES[rand.nextInt(PROVINCES.length)];

            // Keywords
            p.keywords = subject;

            posts.add(p);
        }
        return posts;
    }

    static void writeToDB(List<PostData> posts) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            String sql = "INSERT INTO posts (platform, post_id, author, content, publish_time, " +
                    "likes, comments, shares, province, sentiment, sentiment_score, keywords, analyze_status, crawl_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE likes=VALUES(likes)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batch = 0;
                for (PostData p : posts) {
                    ps.setString(1, p.platform);
                    ps.setString(2, p.postId);
                    ps.setString(3, p.author);
                    ps.setString(4, p.content);
                    ps.setTimestamp(5, Timestamp.valueOf(p.publishTime));
                    ps.setInt(6, p.likes);
                    ps.setInt(7, p.comments);
                    ps.setInt(8, p.shares);
                    ps.setString(9, p.province);
                    if (p.sentiment != null) {
                        ps.setInt(10, p.sentiment);
                    } else {
                        ps.setNull(10, Types.TINYINT);
                    }
                    if (p.sentimentScore != null) {
                        ps.setFloat(11, p.sentimentScore);
                    } else {
                        ps.setNull(11, Types.FLOAT);
                    }
                    ps.setString(12, p.keywords);
                    ps.setInt(13, p.analyzeStatus != null ? p.analyzeStatus : 1);
                    ps.setTimestamp(14, Timestamp.valueOf(p.crawlTime));
                    ps.addBatch();

                    batch++;
                    if (batch % 500 == 0) {
                        ps.executeBatch();
                        conn.commit();
                        System.out.println("  已写入 " + batch + " 条...");
                    }
                }
                ps.executeBatch();
                conn.commit();
            }

            // Verify
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM posts")) {
                rs.next();
                System.out.println("数据库总记录数: " + rs.getInt(1));
            }
        }
    }

    static class PostData {
        String platform;
        String postId;
        String author;
        String content;
        LocalDateTime publishTime;
        int likes;
        int comments;
        int shares;
        String province;
        Integer sentiment;
        Float sentimentScore;
        String keywords;
        Integer analyzeStatus;
        LocalDateTime crawlTime;
    }
}
