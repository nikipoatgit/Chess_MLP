package com.chessmind.ml;

import com.chessmind.engine.StockfishClient;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;

/** Move policy backed by one Stockfish UCI process. */
public final class StockfishMovePolicy implements MovePolicy, AutoCloseable {

    private final StockfishClient client;
    private final int depth;

    public StockfishMovePolicy(String binaryPath, int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive");
        }
        this.client = new StockfishClient(binaryPath);
        this.depth = depth;
    }

    @Override
    public Move chooseMove(Board board) throws IOException {
        StockfishClient.EngineAnalysis analysis = client.analyzeFen(board.getFen(), depth);
        String bestMove = analysis.bestMove();
        if (bestMove == null || bestMove.length() < 4 || "(none)".equalsIgnoreCase(bestMove)) {
            throw new IOException("Stockfish returned no move for position: " + board.getFen());
        }

        Square from = Square.fromValue(bestMove.substring(0, 2).toUpperCase());
        Square to = Square.fromValue(bestMove.substring(2, 4).toUpperCase());
        Move move;
        if (bestMove.length() >= 5) {
            Piece promotion = switch (Character.toLowerCase(bestMove.charAt(4))) {
                case 'q' -> board.getSideToMove() == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
                case 'r' -> board.getSideToMove() == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                case 'b' -> board.getSideToMove() == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
                case 'n' -> board.getSideToMove() == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
                default -> throw new IOException("Unsupported Stockfish promotion: " + bestMove);
            };
            move = new Move(from, to, promotion);
        } else {
            move = new Move(from, to);
        }
        if (!board.isMoveLegal(move, true)) {
            throw new IOException("Stockfish returned an illegal move: " + bestMove);
        }
        return move;
    }

    @Override
    public void close() {
        client.close();
    }
}
