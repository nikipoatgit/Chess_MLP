package com.chessmind.ml;

import com.chessmind.chess.BoardEncoder;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ActivationLayer;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deep Multi-Layer Perceptron (MLP) for Chess move evaluation.
 * Architecture: 1,609 -> 1,024 -> 512 -> 256 -> 4,096
 * Uses a 4,096-class policy output. At inference, its probabilities are
 * renormalized over legal moves, guaranteeing a legal recommendation.
 */
public class ChessModel {

    public static final int INPUT_SIZE = 1609;
    public static final int OUTPUT_SIZE = 4096;
    /** The model used by the application across launches. */
    public static final File DEFAULT_WEIGHTS_FILE = new File("model/weights.zip");

    private final MultiLayerNetwork network;

    public record MovePrediction(Move move, String san, double probability, int index) implements Comparable<MovePrediction> {
        @Override
        public int compareTo(MovePrediction o) {
            return Double.compare(o.probability, this.probability); // Descending order
        }
    }

    public ChessModel() {
        this.network = buildNetwork();
        this.network.init();
    }

    public ChessModel(MultiLayerNetwork network) {
        this.network = network;
    }

    private static MultiLayerNetwork buildNetwork() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .weightInit(WeightInit.XAVIER)
                .updater(new NoOp())
                .list()
                .layer(0, new DenseLayer.Builder().nIn(INPUT_SIZE).nOut(1024)
                    .activation(Activation.IDENTITY).build())
                .layer(1, new BatchNormalization.Builder().build())
                .layer(2, new ActivationLayer(Activation.RELU))
                .layer(3, new DenseLayer.Builder().nIn(1024).nOut(512)
                    .activation(Activation.IDENTITY).build())
                .layer(4, new BatchNormalization.Builder().build())
                .layer(5, new ActivationLayer(Activation.RELU))
                .layer(6, new DenseLayer.Builder().nIn(512).nOut(256)
                    .activation(Activation.IDENTITY).build())
                .layer(7, new BatchNormalization.Builder().build())
                .layer(8, new ActivationLayer(Activation.RELU))
                .layer(9, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .nIn(256).nOut(4096)
                        .activation(Activation.SOFTMAX).build())
                .build();

        return new MultiLayerNetwork(conf);
    }

    public MultiLayerNetwork getNetwork() {
        return network;
    }

    public Move predictBestMove(Board board) {
        INDArray input = BoardEncoder.encode(board);
        return predictBestMove(board, input);
    }

    public Move predictBestMove(Board board, INDArray inputVector) {
        List<MovePrediction> predictions = predictTopMoves(board, inputVector, 1);
        return predictions.isEmpty() ? null : predictions.get(0).move();
    }

    public List<MovePrediction> predictTopMoves(Board board, int topK) {
        INDArray input = BoardEncoder.encode(board);
        return predictTopMoves(board, input, topK);
    }

    public synchronized List<MovePrediction> predictTopMoves(Board board, INDArray inputVector, int topK) {
        List<Move> legalMoves = board.legalMoves();
        if (legalMoves.isEmpty()) {
            return Collections.emptyList();
        }

        // The output layer is a global softmax. Filtering and renormalizing its
        // values is mathematically equivalent to applying softmax to only the
        // legal move logits, while avoiding illegal choices entirely.
        INDArray probabilities = network.output(inputVector, false);
        boolean isBlack = (board.getSideToMove() == Side.BLACK);

        // Map legal moves to model indices and gather probabilities.
        List<Integer> indices = new ArrayList<>(legalMoves.size());
        List<Double> probabilityValues = new ArrayList<>(legalMoves.size());

        double probabilitySum = 0.0;
        for (Move move : legalMoves) {
            int from = move.getFrom().ordinal();
            int to = move.getTo().ordinal();

            if (isBlack) {
                from = 63 - from;
                to = 63 - to;
            }

            int index = (from * 64) + to;
            indices.add(index);
            double probability = probabilities.getDouble(0, index);
            probabilityValues.add(probability);
            probabilitySum += probability;
        }

        List<MovePrediction> predictions = new ArrayList<>(legalMoves.size());
        for (int i = 0; i < legalMoves.size(); i++) {
            Move move = legalMoves.get(i);
            int idx = indices.get(i);
            double prob = (probabilitySum > 0.0)
                    ? (probabilityValues.get(i) / probabilitySum)
                    : (1.0 / legalMoves.size());
            String san = move.getSan();
            if (san == null || san.isEmpty()) {
                san = move.toString();
            }
            predictions.add(new MovePrediction(move, san, prob, idx));
        }

        Collections.sort(predictions);

        if (topK > 0 && predictions.size() > topK) {
            return predictions.subList(0, topK);
        }
        return predictions;
    }

    public static int moveToModelIndex(Move move, boolean isBlack) {
        int from = move.getFrom().ordinal();
        int to = move.getTo().ordinal();
        if (isBlack) {
            from = 63 - from;
            to = 63 - to;
        }
        return (from * 64) + to;
    }

    public void save(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create model directory: " + parent);
        }
        ModelSerializer.writeModel(network, file, true);
    }

    /**
     * Saves without leaving a partially-written model at the persistent path
     * if the application is interrupted while DL4J is writing the archive.
     */
    public void saveAtomically(File file) throws IOException {
        File absoluteFile = file.getAbsoluteFile();
        File parent = absoluteFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create model directory: " + parent);
        }

        File temporaryFile = new File(absoluteFile.getPath() + ".tmp");
        save(temporaryFile);
        try {
            Files.move(temporaryFile.toPath(), absoluteFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile.toPath(), absoluteFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ChessModel load(File file) throws IOException {
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(file);
        return new ChessModel(net);
    }

}
