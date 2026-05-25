package com.sentiment;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

/**
 * 快速冒烟测试: 验证数据加载、编码、词表构建
 */
public class SmokeTest {
    static final Charset GBK = Charset.forName("GBK");
    static final String DATA_ROOT = System.getProperty("data.root", "D:/shuju/ChnSentiCorp");
    static final String TRAIN_POS = DATA_ROOT + "/train/train/pos";
    static final String TRAIN_NEG = DATA_ROOT + "/train/train/neg";
    static final String TEST_DIR  = DATA_ROOT + "/test/test";

    public static void main(String[] args) throws Exception {
        // 1. 测试编码
        System.out.println("=== 编码测试 ===");
        File posSample = new File(TRAIN_POS + "/pos (1).txt");
        String text = readText(posSample);
        System.out.println("正样本示例: " + text.substring(0, Math.min(100, text.length())));

        File negSample = new File(TRAIN_NEG + "/neg (1).txt");
        text = readText(negSample);
        System.out.println("负样本示例: " + text.substring(0, Math.min(100, text.length())));

        File testSample = new File(TEST_DIR + "/00001.txt");
        text = readText(testSample);
        System.out.println("测试样本示例: " + text.substring(0, Math.min(100, text.length())));

        // 2. 统计文件数量
        System.out.println("\n=== 文件统计 ===");
        System.out.println("正样本文件数: " + countTxt(TRAIN_POS));
        System.out.println("负样本文件数: " + countTxt(TRAIN_NEG));
        System.out.println("测试文件数:   " + countTxt(TEST_DIR));

        // 3. 字符频率统计
        System.out.println("\n=== 字符频率 (Top 20) ===");
        Map<Character, Integer> freq = new HashMap<>();
        for (File f : listTxt(TRAIN_POS)) {
            for (char c : readText(f).toCharArray()) freq.merge(c, 1, Integer::sum);
        }
        for (File f : listTxt(TRAIN_NEG)) {
            for (char c : readText(f).toCharArray()) freq.merge(c, 1, Integer::sum);
        }
        System.out.println("总字符数: " + freq.size());

        freq.entrySet().stream()
            .sorted(Map.Entry.<Character, Integer>comparingByValue().reversed())
            .limit(20)
            .forEach(e -> System.out.printf("  '%s' (%04x): %d%n", e.getKey(), (int)e.getKey(), e.getValue()));

        System.out.println("\n=== 冒烟测试通过! ===");
    }

    static String readText(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), GBK)
            .replace("﻿", "").replace("\r", "").replace("\n", "")
            .replace("content>", "").trim();
    }

    static int countTxt(String dir) throws IOException {
        return listTxt(dir).size();
    }

    static List<File> listTxt(String dir) throws IOException {
        List<File> files = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(Paths.get(dir), "*.txt")) {
            for (Path p : s) files.add(p.toFile());
        }
        return files;
    }
}
