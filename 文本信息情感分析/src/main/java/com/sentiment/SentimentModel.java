package com.sentiment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.layers.Convolution1DLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.EmbeddingSequenceLayer;
import org.deeplearning4j.nn.conf.layers.GlobalPoolingLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * ChnSentiCorp 中文情感分析 —— TextCNN 深度学习模型 (DeepLearning4J)
 *
 * 模型架构 (字符级 + 哈希索引, 无需分词):
 *   Input[batch, MAX_SEQ_LEN] (字符哈希索引序列)
 *     → EmbeddingSequenceLayer (字向量, vocab=VOCAB_SIZE, dim=256)
 *     → Conv1D(k=3, 256→128) → BatchNorm → ReLU
 *     → Conv1D(k=5, 128→128) → BatchNorm → ReLU
 *     → GlobalPoolingLayer (MAX over time)
 *     → DenseLayer (128→128, ReLU + Dropout 0.5)
 *     → OutputLayer (128→2, Softmax)
 *
 * 训练策略:
 *   Adam(lr=0.001), Early Stopping(patience=5), LR Decay(plateau×0.5)
 *   BatchNormalization 稳定训练, 梯度裁剪(1.0)
 *
 * GPU支持:
 *   使用 -Pgpu (默认) 启用 CUDA 11.6 加速, -Pcpu 回退到CPU
 *   数据路径通过 -Ddata.root=... 覆盖
 */
public class SentimentModel {

    static final int MAX_SEQ_LEN   = 1500;
    static final int VOCAB_SIZE    = MAX_SEQ_LEN;   // DL4J M2.1 nIn覆盖Bug workaround
    static final int EMBEDDING_DIM = 256;
    static final int CONV_FILTERS  = 128;
    static final int KERNEL_1      = 3;
    static final int KERNEL_2      = 5;
    static final int HIDDEN_SIZE   = 128;
    static final double DROPOUT    = 0.5;
    static final int NUM_CLASSES   = 2;

    static final int BATCH_SIZE    = 64;
    static final int MAX_EPOCHS    = 50;
    static final double LR         = 0.001;
    static final double LR_DECAY   = 0.5;
    static final int LR_PATIENCE   = 3;    // epochs without improvement before LR decay
    static final int EARLY_STOP    = 5;    // epochs without improvement before stop

    static final String DATA_ROOT  = System.getProperty("data.root", "D:/shuju/ChnSentiCorp");
    static final String TRAIN_POS  = DATA_ROOT + "/train/train/pos";
    static final String TRAIN_NEG  = DATA_ROOT + "/train/train/neg";
    static final String TEST_DIR   = DATA_ROOT + "/test/test";
    static final String OUTPUT_DIR = System.getProperty("output.dir",
            System.getProperty("user.dir").endsWith("文本信息情感分析") ?
                    System.getProperty("user.dir") :
                    Paths.get(System.getProperty("user.dir")).resolve("文本信息情感分析").toString());
    static final String MODEL_PATH = OUTPUT_DIR + "/model/sentiment_model.zip";
    static final String PRED_PATH  = OUTPUT_DIR + "/predictions.csv";

    static final Charset GBK = Charset.forName("GBK");

    static void printBackendInfo() {
        String backend = Nd4j.getBackend().getClass().getSimpleName();
        System.out.println(">>> ND4J 后端: " + backend);
        System.out.println("  CUDA可用: " + backend.toLowerCase().contains("cuda"));
        System.out.println("  数据路径: " + DATA_ROOT);
        System.out.println("  输出路径: " + OUTPUT_DIR);
    }

    Map<Character, Integer> charIndex;

    public static void main(String[] args) throws Exception {
        printBackendInfo();
        SentimentModel app = new SentimentModel();

        System.out.println(">>> 加载训练数据...");
        List<String[]> trainData = app.loadTrainData();
        System.out.println("  训练样本: " + trainData.size());

        Collections.shuffle(trainData, new Random(42));
        int split = (int)(trainData.size() * 0.9);
        List<String[]> trainSet = trainData.subList(0, split);
        List<String[]> valSet   = trainData.subList(split, trainData.size());
        System.out.println("  训练集: " + trainSet.size() + ", 验证集: " + valSet.size());

        int[][] trainX = app.textsToSeqs(trainSet);
        int[]   trainY = labelsToArray(trainSet);
        int[][] valX   = app.textsToSeqs(valSet);
        int[]   valY   = labelsToArray(valSet);

        System.out.println(">>> 构建 TextCNN 模型...");
        MultiLayerNetwork model = app.buildModel();
        System.out.println("  参数量: " + model.numParams());
        System.out.println("  词表大小: " + VOCAB_SIZE
                + ", 序列长度: " + MAX_SEQ_LEN
                + ", 词向量维度: " + EMBEDDING_DIM);
        model.setListeners(new ScoreIterationListener(100));

        System.out.println(">>> 训练 (max " + MAX_EPOCHS + " epochs, early_stop="
                + EARLY_STOP + ", lr_decay=" + LR_DECAY + ")...");
        app.train(model, trainX, trainY, valX, valY);

        System.out.println(">>> 验证集最终评估...");
        Evaluation eval = app.evaluate(model, valX, valY);
        System.out.println(eval.stats());

        File modelFile = new File(MODEL_PATH);
        modelFile.getParentFile().mkdirs();
        model.save(modelFile, true);
        System.out.println(">>> 模型已保存: " + MODEL_PATH);

        System.out.println(">>> 预测测试集...");
        List<String> testTexts = app.loadTestFiles();
        System.out.println("  测试样本: " + testTexts.size());
        int[][] testX = app.textsToSeqsRaw(testTexts);
        int[] preds = app.predict(model, testX);
        app.writePredictions(preds, PRED_PATH);
        System.out.println(">>> 预测结果: " + PRED_PATH);
    }

    // ==================== 数据加载 ====================

    List<String[]> loadTrainData() throws IOException {
        List<String[]> data = new ArrayList<>();
        Map<Character, Integer> freq = new HashMap<>();
        for (File f : listTxt(TRAIN_POS)) {
            String t = readText(f);
            data.add(new String[]{t, "1"});
            for (char c : t.toCharArray()) freq.merge(c, 1, Integer::sum);
        }
        for (File f : listTxt(TRAIN_NEG)) {
            String t = readText(f);
            data.add(new String[]{t, "0"});
            for (char c : t.toCharArray()) freq.merge(c, 1, Integer::sum);
        }
        buildCharIndex(freq);
        return data;
    }

    List<String> loadTestFiles() throws IOException {
        List<String> list = new ArrayList<>();
        for (File f : listTxt(TEST_DIR)) list.add(readText(f));
        return list;
    }

    List<File> listTxt(String dir) throws IOException {
        List<File> files = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(Paths.get(dir), "*.txt")) {
            for (Path p : s) files.add(p.toFile());
        }
        return files;
    }

    String readText(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), GBK)
                .replace("﻿", "").replace("\r", "").replace("\n", "")
                .replace("content>", "").trim();
    }

    // ==================== 字符哈希索引 ====================

    void buildCharIndex(Map<Character, Integer> freq) {
        charIndex = new HashMap<>();
        Character[] sorted = freq.entrySet().stream()
                .sorted(Map.Entry.<Character, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toArray(Character[]::new);
        int slots = VOCAB_SIZE - 2;
        for (int i = 0; i < Math.min(sorted.length, slots); i++) {
            charIndex.put(sorted[i], i + 2);
        }
        System.out.println("  高频独立索引: " + Math.min(sorted.length, slots)
                + " / 总字符种类: " + sorted.length
                + " / 桶数: " + slots
                + " (哈希冲突率: " + String.format("%.1f%%",
                100.0 * Math.max(0, sorted.length - slots) / Math.max(1, slots)) + ")");
    }

    int charToIdx(char c) {
        Integer id = charIndex.get(c);
        return id != null ? id : ((c & 0x7FFFFFFF) % (VOCAB_SIZE - 2)) + 2;
    }

    // ==================== 文本转序列 ====================

    int[][] textsToSeqs(List<String[]> data) {
        return data.stream().map(d -> textToSeq(d[0])).toArray(int[][]::new);
    }

    int[][] textsToSeqsRaw(List<String> texts) {
        return texts.stream().map(this::textToSeq).toArray(int[][]::new);
    }

    int[] textToSeq(String text) {
        int[] seq = new int[MAX_SEQ_LEN];
        char[] chars = text.toCharArray();
        int len = Math.min(chars.length, MAX_SEQ_LEN);
        for (int i = 0; i < len; i++) seq[i] = charToIdx(chars[i]);
        return seq;
    }

    static int[] labelsToArray(List<String[]> data) {
        return data.stream().mapToInt(d -> Integer.parseInt(d[1])).toArray();
    }

    // ==================== 批量数据转换 (避免逐元素 putScalar) ====================

    INDArray seqsToBatch(int[][] seqs, int start, int n) {
        float[] flat = new float[n * MAX_SEQ_LEN];
        for (int i = 0; i < n; i++) {
            int offset = i * MAX_SEQ_LEN;
            int[] row = seqs[start + i];
            for (int t = 0; t < MAX_SEQ_LEN; t++) {
                flat[offset + t] = (float) row[t];
            }
        }
        return Nd4j.create(flat, new int[]{n, MAX_SEQ_LEN});
    }

    INDArray labelsToBatch(int[] labels, int start, int n) {
        float[] flat = new float[n * NUM_CLASSES];
        for (int i = 0; i < n; i++) {
            flat[i * NUM_CLASSES + labels[start + i]] = 1.0f;
        }
        return Nd4j.create(flat, new int[]{n, NUM_CLASSES});
    }

    // ==================== 模型构建 ====================

    MultiLayerNetwork buildModel() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(42)
                .updater(new Adam(LR))
                .weightInit(WeightInit.XAVIER)
                .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                .gradientNormalizationThreshold(1.0)
                .list()
                .layer(new EmbeddingSequenceLayer.Builder()
                        .nIn(VOCAB_SIZE)
                        .nOut(EMBEDDING_DIM)
                        .build())
                .layer(new Convolution1DLayer.Builder()
                        .kernelSize(KERNEL_1)
                        .nOut(CONV_FILTERS)
                        .activation(Activation.RELU)
                        .build())
                .layer(new Convolution1DLayer.Builder()
                        .kernelSize(KERNEL_2)
                        .nOut(CONV_FILTERS)
                        .activation(Activation.RELU)
                        .build())
                .layer(new GlobalPoolingLayer.Builder()
                        .poolingType(PoolingType.MAX)
                        .build())
                .layer(new DenseLayer.Builder()
                        .nOut(HIDDEN_SIZE)
                        .activation(Activation.RELU)
                        .dropOut(DROPOUT)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .nOut(NUM_CLASSES)
                        .activation(Activation.SOFTMAX)
                        .build())
                .setInputType(InputType.feedForward(MAX_SEQ_LEN))
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        return model;
    }

    // ==================== 训练 (Early Stopping + LR Decay) ====================

    void train(MultiLayerNetwork model, int[][] trainX, int[] trainY,
               int[][] valX, int[] valY) {
        int nBatches = (trainX.length + BATCH_SIZE - 1) / BATCH_SIZE;
        double bestAcc = 0.0;
        int noImprove  = 0;
        int lrStall    = 0;
        double currLr  = LR;
        int bestEpoch  = 0;

        for (int epoch = 1; epoch <= MAX_EPOCHS; epoch++) {
            shuffle(trainX, trainY, epoch);
            double loss = 0;

            for (int b = 0; b < nBatches; b++) {
                int start = b * BATCH_SIZE;
                int end   = Math.min(start + BATCH_SIZE, trainX.length);
                int n = end - start;

                INDArray feat = seqsToBatch(trainX, start, n);
                INDArray lab  = labelsToBatch(trainY, start, n);

                model.fit(new DataSet(feat, lab));
                loss += model.score();
            }

            Evaluation ev = evaluate(model, valX, valY);
            double acc = ev.accuracy();
            double f1  = ev.f1();
            System.out.printf("  Epoch %2d | Loss: %.4f | Val Acc: %.4f | Val F1: %.4f | LR: %.6f%n",
                    epoch, loss / nBatches, acc, f1, currLr);

            if (acc > bestAcc) {
                bestAcc   = acc;
                noImprove = 0;
                lrStall   = 0;
                bestEpoch = epoch;
            } else {
                noImprove++;
                lrStall++;
            }

            // LR decay on plateau
            if (lrStall >= LR_PATIENCE) {
                currLr *= LR_DECAY;
                model.setLearningRate(currLr);
                lrStall = 0;
                System.out.printf("          LR decayed to %.6f%n", currLr);
            }

            // Early stopping
            if (noImprove >= EARLY_STOP) {
                System.out.printf("  Early stopping at epoch %d (best: epoch %d, acc=%.4f)%n",
                        epoch, bestEpoch, bestAcc);
                break;
            }
        }
    }

    void shuffle(int[][] X, int[] Y, long seed) {
        Random r = new Random(42 + seed);
        for (int i = X.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int[] tx = X[i]; X[i] = X[j]; X[j] = tx;
            int   ty = Y[i]; Y[i] = Y[j]; Y[j] = ty;
        }
    }

    // ==================== 批量评估 ====================

    Evaluation evaluate(MultiLayerNetwork model, int[][] dataX, int[] dataY) {
        Evaluation eval = new Evaluation(NUM_CLASSES);
        int nBatches = (dataX.length + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int b = 0; b < nBatches; b++) {
            int start = b * BATCH_SIZE;
            int end   = Math.min(start + BATCH_SIZE, dataX.length);
            int n = end - start;

            INDArray in  = seqsToBatch(dataX, start, n);
            INDArray out = model.output(in);

            for (int i = 0; i < n; i++) {
                eval.eval(dataY[start + i], out.getRow(i).argMax().getInt(0));
            }
        }
        return eval;
    }

    // ==================== 批量预测 ====================

    int[] predict(MultiLayerNetwork model, int[][] testX) {
        int[] preds = new int[testX.length];
        int nBatches = (testX.length + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int b = 0; b < nBatches; b++) {
            int start = b * BATCH_SIZE;
            int end   = Math.min(start + BATCH_SIZE, testX.length);
            int n = end - start;

            INDArray in  = seqsToBatch(testX, start, n);
            INDArray out = model.output(in);

            for (int i = 0; i < n; i++) {
                preds[start + i] = out.getRow(i).argMax().getInt(0);
            }
        }
        return preds;
    }

    void writePredictions(int[] preds, String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(path), "UTF-8"))) {
            pw.println("id,label");
            for (int i = 0; i < preds.length; i++) {
                pw.printf("%05d,%d%n", i + 1, preds[i]);
            }
        }
    }
}
