package com.chessmind;

import com.chessmind.ml.ChessModel;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LegalMaskingTest {

    private static ChessModel model;

    @BeforeAll
    static void setup() {
        model = new ChessModel();
    }

    @Test
    @DisplayName("Verify predicted best move is strictly a legal move in starting position")
    void testBestMoveLegalityInitial() {
        Board board = new Board();
        List<Move> legalMoves = board.legalMoves();

        Move bestMove = model.predictBestMove(board);
        assertNotNull(bestMove, "Model should return a move for non-terminal position");
        assertTrue(legalMoves.contains(bestMove), "Predicted move must be in legal moves list");
    }

    @Test
    @DisplayName("Verify predicted best move is strictly legal in complex midgame position")
    void testBestMoveLegalityMidgame() {
        Board board = new Board();
        // Sicilian Defense, Najdorf variation position
        board.loadFromFen("r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R w KQkq - 2 7");

        List<Move> legalMoves = board.legalMoves();
        Move bestMove = model.predictBestMove(board);

        assertNotNull(bestMove);
        assertTrue(legalMoves.contains(bestMove), "Predicted midgame move must be legal");
    }

    @Test
    @DisplayName("Verify top-k candidates are legal, sorted descending, and sum to approximately 1.0")
    void testTopCandidatesProperties() {
        Board board = new Board();
        int topK = 5;
        List<ChessModel.MovePrediction> topMoves = model.predictTopMoves(board, topK);

        assertFalse(topMoves.isEmpty());
        assertTrue(topMoves.size() <= topK);

        List<Move> legalMoves = board.legalMoves();
        double prevProb = Double.MAX_VALUE;
        for (ChessModel.MovePrediction pred : topMoves) {
            assertTrue(legalMoves.contains(pred.move()), "Every recommended move must be legal");
            assertTrue(pred.probability() <= prevProb, "Moves must be sorted descending by probability");
            assertTrue(pred.probability() >= 0.0 && pred.probability() <= 1.0, "Probability must be between 0 and 1");
            prevProb = pred.probability();
        }
    }
}
