package com.sentiment.analyzer;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

@Service
public class SentimentService {

    private static final Logger log = LoggerFactory.getLogger(SentimentService.class);
    private static final int MAX_SEQ_LEN = 1500;   // Input sequence length
    private static final int VOCAB_SIZE = 500;      // Embedding nIn (must match model)

    @Value("${sentiment.model.path}")
    private String modelPath;

    @Value("${sentiment.analysis.batch-size:64}")
    private int batchSize;

    private MultiLayerNetwork model;
    private Map<Character, Integer> charIndex;
    private boolean gpuEnabled = false;

    @PostConstruct
    public void init() {
        try {
            File file = new File(modelPath);
            if (!file.exists()) {
                log.warn("Model file not found: {}. Sentiment analysis disabled.", modelPath);
                return;
            }
            model = MultiLayerNetwork.load(file, true);
            String backend = Nd4j.getBackend().getClass().getSimpleName();
            gpuEnabled = backend.toLowerCase().contains("cuda");

            // Read actual nIn from model
            int modelVocabSize = (int) model.getLayerWiseConfigurations()
                    .getConf(0).getLayer()
                    .getClass().getMethod("getNIn")
                    .invoke(model.getLayerWiseConfigurations().getConf(0).getLayer());

            log.info("Sentiment model loaded. Backend: {}, GPU: {}, Embedding nIn: {}",
                    backend, gpuEnabled, modelVocabSize);

            if (modelVocabSize != VOCAB_SIZE) {
                log.warn("Model vocab size ({}) != expected ({}). Using model's value.",
                        modelVocabSize, VOCAB_SIZE);
            }

            log.info("Vocab: {}, SeqLen: {}", VOCAB_SIZE, MAX_SEQ_LEN);
        } catch (Exception e) {
            log.error("Failed to load sentiment model", e);
        }
    }

    public boolean isReady() {
        return model != null;
    }

    public boolean isGpuEnabled() {
        return gpuEnabled;
    }

    public List<float[]> batchPredict(List<String> texts) {
        if (model == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>();
        int nBatches = (texts.size() + batchSize - 1) / batchSize;

        for (int b = 0; b < nBatches; b++) {
            int start = b * batchSize;
            int end = Math.min(start + batchSize, texts.size());
            int n = end - start;

            INDArray batch = textsToBatch(texts, start, n);
            INDArray output = model.output(batch);

            for (int i = 0; i < n; i++) {
                float negProb = output.getFloat(i, 0);
                float posProb = output.getFloat(i, 1);
                int sentiment = posProb > negProb ? 1 : 0;
                float confidence = Math.max(posProb, negProb);
                results.add(new float[]{sentiment, confidence});
            }
        }
        return results;
    }

    public float[] predict(String text) {
        List<float[]> results = batchPredict(Collections.singletonList(text));
        return results.isEmpty() ? new float[]{-1, 0} : results.get(0);
    }

    private INDArray textsToBatch(List<String> texts, int start, int n) {
        float[] flat = new float[n * MAX_SEQ_LEN];
        for (int i = 0; i < n; i++) {
            String text = texts.get(start + i);
            int offset = i * MAX_SEQ_LEN;
            char[] chars = text.toCharArray();
            int len = Math.min(chars.length, MAX_SEQ_LEN);
            for (int t = 0; t < len; t++) {
                flat[offset + t] = (float) charToIdx(chars[t]);
            }
        }
        return Nd4j.create(flat, new int[]{n, MAX_SEQ_LEN});
    }

    /**
     * Map character to embedding index.
     * Must produce indices in range [0, VOCAB_SIZE-1].
     * Matches training logic: hash-based index with VOCAB_SIZE buckets.
     */
    private int charToIdx(char c) {
        // Hash character into [2, VOCAB_SIZE-1] range
        // Index 0 = padding, 1 = unknown
        return ((c & 0x7FFFFFFF) % (VOCAB_SIZE - 2)) + 2;
    }
}
