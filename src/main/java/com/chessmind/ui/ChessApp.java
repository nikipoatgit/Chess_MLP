package com.chessmind.ui;

import com.chessmind.chess.Game;
import com.chessmind.ml.ChessModel;
import com.chessmind.ml.StockfishMovePolicy;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main JavaFX Application for ChessMind.
 * Runs an automatic MLP-versus-Stockfish game and displays its state.
 */
public class ChessApp extends Application {

    private final Game game = new Game();
    private ChessModel model;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong positionVersion = new AtomicLong();

    private BoardView boardView;
    private RecommendationPanel recPanel;
    private StockfishMovePolicy stockfishPolicy;

    @Override
    public void start(Stage primaryStage) {
        // Initialize model in background or lazy
        try {
            model = ChessModel.load(ChessModel.DEFAULT_WEIGHTS_FILE);
            System.out.println("Loaded model weights from " + ChessModel.DEFAULT_WEIGHTS_FILE.getPath());
        } catch (Exception e) {
            throw new IllegalStateException("Could not load required model weights from "
                    + ChessModel.DEFAULT_WEIGHTS_FILE.getPath()
                    + ". Provide a DL4J weights.zip exported from the Colab model.", e);
        }

        boardView = new BoardView(game);
        recPanel = new RecommendationPanel();

        recPanel.getPlayButton().setOnAction(e -> startGame());
        recPanel.getResetButton().setOnAction(e -> resetGame());
        recPanel.getStockfishModeBox().setOnAction(e -> resetStockfishPolicy());
        resetStockfishPolicy();

        StackPane boardContainer = new StackPane(boardView);
        boardContainer.setAlignment(Pos.CENTER);
        boardContainer.getStyleClass().add("board-container");

        BorderPane gameRoot = new BorderPane();
        gameRoot.setCenter(boardContainer);
        gameRoot.setRight(recPanel);

        Scene scene = new Scene(gameRoot, 1020, 720);
        String css = getClass().getResource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        primaryStage.setTitle("ChessMind - Play Stockfish");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(850);
        primaryStage.setMinHeight(680);
        primaryStage.show();

        updateUiAfterStateChange(null);
    }

    private void startGame() {
        game.reset();
        boardView.setLastMove(null);
        boardView.updateBoard();
        recPanel.getPlayButton().setDisable(true);
        recPanel.setStatus("Game in progress...");
        long version = positionVersion.incrementAndGet();
        aiExecutor.submit(() -> playNextMove(version));
    }

    private void resetGame() {
        positionVersion.incrementAndGet();
        game.reset();
        boardView.setLastMove(null);
        boardView.updateBoard();
        recPanel.getPlayButton().setDisable(false);
        recPanel.setStatus(game.getStatusMessage());
    }

    private void playNextMove(long version) {
        if (positionVersion.get() != version || game.isGameOver()) {
            finishGame(version);
            return;
        }

        try {
            Board snapshot = game.copyBoard();
            Move move = snapshot.getSideToMove() == Side.WHITE
                    ? model.predictBestMove(snapshot)
                    : stockfishPolicy.chooseMove(snapshot);
            if (move == null) {
                throw new IllegalStateException("The MLP returned no legal move.");
            }

            Platform.runLater(() -> {
                if (positionVersion.get() != version || !game.makeMove(move)) {
                    return;
                }
                boardView.setLastMove(move);
                boardView.updateBoard();
                recPanel.setStatus(game.isGameOver() ? resultMessage() : "Game in progress...");
                aiExecutor.submit(() -> playNextMove(version));
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                if (positionVersion.get() == version) {
                    recPanel.setStatus("Game stopped: " + e.getMessage());
                    recPanel.getPlayButton().setDisable(false);
                }
            });
        }
    }

    private void finishGame(long version) {
        Platform.runLater(() -> {
            if (positionVersion.get() == version) {
                recPanel.setStatus(resultMessage());
                recPanel.getPlayButton().setDisable(false);
            }
        });
    }

    private String resultMessage() {
        String status = game.getStatusMessage();
        if (status.startsWith("Checkmate!")) {
            return status.contains("WHITE") ? "MLP wins - Checkmate!" : "Stockfish wins - Checkmate!";
        }
        return status;
    }

    private void resetStockfishPolicy() {
        if (stockfishPolicy != null) {
            stockfishPolicy.close();
        }
        stockfishPolicy = new StockfishMovePolicy(
                System.getProperty("chessmind.stockfish.path", "stockfish"),
                recPanel.getStockfishDepth());
    }

    private void updateUiAfterStateChange(Move lastMove) {
        boardView.setLastMove(lastMove);
        boardView.updateBoard();
        recPanel.setStatus(game.getStatusMessage());
    }

    @Override
    public void stop() {
        if (stockfishPolicy != null) {
            stockfishPolicy.close();
        }
        aiExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
