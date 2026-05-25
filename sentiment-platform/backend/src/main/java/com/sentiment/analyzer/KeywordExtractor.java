package com.sentiment.analyzer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeywordExtractor {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
            "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
            "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
            "它", "们", "那", "被", "从", "对", "把", "与", "以", "及",
            "等", "但", "而", "或", "又", "如", "所", "之", "其", "这个",
            "那个", "什么", "怎么", "可以", "还是", "因为", "所以",
            "如果", "虽然", "但是", "然后", "已经", "正在", "可能", "应该"
    ));

    /**
     * Extract top N keywords from text using TF-IDF.
     */
    public String extract(String text, int topN) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        List<Term> terms = StandardTokenizer.segment(text);
        Map<String, Integer> wordFreq = new HashMap<>();

        for (Term term : terms) {
            String word = term.word.trim().toLowerCase();
            if (word.length() < 2 || STOP_WORDS.contains(word)) {
                continue;
            }
            String nature = term.nature != null ? term.nature.toString() : "";
            // Keep nouns, verbs, adjectives
            if (nature.startsWith("n") || nature.startsWith("v") || nature.startsWith("a")) {
                wordFreq.merge(word, 1, Integer::sum);
            }
        }

        return wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }
}
