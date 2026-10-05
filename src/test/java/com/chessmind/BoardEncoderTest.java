package com.chessmind;

import com.chessmind.chess.BoardEncoder;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoardEncoderTest {

    @Test
    @DisplayName("Verify feature tensor shape is strictly [1, 1609]")
    void testFeatureVectorShape() {
        Board board = new Board();
        INDArray tensor = BoardEncoder.encode(board);

        assertEquals(2, tensor.shape().length);
        assertEquals(1, tensor.shape()[0]);
        assertEquals(1609, tensor.shape()[1]);
    }

    @Test
    @DisplayName("Verify occupancy planes count in standard initial position")
    void testInitialOccupancyPlanes() {
        Board board = new Board();
        float[] vec = BoardEncoder.encodeToFloatArray(board);

        // Planes 0-11 span indices 0 to 767 (768 total values)
        int pieceCount = 0;
        for (int i = 0; i < 768; i++) {
            if (vec[i] == 1.0f) {
                pieceCount++;
            }
        }
        // Exactly 32 pieces on a standard chess board
        assertEquals(32, pieceCount);
    }

    @Test
    @DisplayName("Verify perspective flipping when Black is to move")
    void testPerspectiveFlipping() {
        Board whiteTurnBoard = new Board();
        float[] whiteVec = BoardEncoder.encodeToFloatArray(whiteTurnBoard);

        // Index 1536 is the original side color: 1.0 for White
        assertEquals(1.0f, whiteVec[1536]);

        // Make a move so it's Black's turn
        Board blackTurnBoard = new Board();
        blackTurnBoard.doMove(new Move("e2e4", Side.WHITE));
        float[] blackVec = BoardEncoder.encodeToFloatArray(blackTurnBoard);

        // Index 1536 must now be 0.0 for Black
        assertEquals(0.0f, blackVec[1536]);
    }

    @Test
    @DisplayName("Verify active reachability features for standard opening")
    void testActiveReachability() {
        Board board = new Board();
        float[] vec = BoardEncoder.encodeToFloatArray(board);

        // Planes 12-17 are active player legal targets (indices 768 to 1151)
        int reachabilityCount = 0;
        for (int i = 768; i < 1152; i++) {
            if (vec[i] == 1.0f) {
                reachabilityCount++;
            }
        }
        // White has 20 legal moves in starting position (some squares targeted by multiple pieces like d3/e3,
        // but reachability plane per piece-type records piece targets)
        assertTrue(reachabilityCount > 0, "Reachability plane must contain active legal targets");
    }
}
