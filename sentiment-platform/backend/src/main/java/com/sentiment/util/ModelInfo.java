package com.sentiment.util;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.layers.EmbeddingSequenceLayer;
import org.deeplearning4j.nn.conf.layers.Layer;

public class ModelInfo {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "D:/A01/sentiment-platform/model/sentiment_model.zip";
        MultiLayerNetwork model = MultiLayerNetwork.load(new java.io.File(path), true);
        MultiLayerConfiguration conf = model.getLayerWiseConfigurations();

        System.out.println("=== Model Layers ===");
        for (int i = 0; i < conf.getConfs().size(); i++) {
            Layer layer = conf.getConf(i).getLayer();
            System.out.printf("Layer %d: %s%n", i, layer.getClass().getSimpleName());
            if (layer instanceof EmbeddingSequenceLayer) {
                EmbeddingSequenceLayer emb = (EmbeddingSequenceLayer) layer;
                System.out.printf("  nIn=%d, nOut=%d%n", emb.getNIn(), emb.getNOut());
            }
        }
        System.out.println("Total params: " + model.numParams());
    }
}
