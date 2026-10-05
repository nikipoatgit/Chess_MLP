package com.chessmind.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;

/**
 * Encodes a Chesslib Board into a 1,609-dimensional feature vector:
 * - Planes 0-11: Piece occupancy (12 x 64 = 768)
 * - Planes 12-23: Reachability / Legal target maps (12 x 64 = 768)
 * - Auxiliary features: 73 features (Color, Castling, EP, Counters, Check)
 * Total: 768 + 768 + 73 = 1,609
 */
public class BoardEncoder {

    public static final int VECTOR_SIZE = 1609;

    public static INDArray encode(Board board) {
        float[] vector = encodeToFloatArray(board);
        return Nd4j.create(vector, new long[]{1, VECTOR_SIZE});
    }

    public static float[] encodeToFloatArray(Board board) {
        float[] vector = new float[VECTOR_SIZE];
        boolean isBlackToMove = (board.getSideToMove() == Side.BLACK);

        // 1. Occupancy Planes (Planes 0-11: 768 features)
        encodeOccupancy(board, vector, isBlackToMove);

        // 2. Reachability Planes (Planes 12-23: 768 features)
        encodeReachability(board, vector, isBlackToMove);

        // 3. Auxiliary Game State Features (73 features starting at index 1536)
        encodeAuxiliary(board, vector, isBlackToMove);

        return vector;
    }

    private static void encodeOccupancy(Board board, float[] vec, boolean isBlack) {
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            Piece piece = board.getPiece(sq);
            if (piece == Piece.NONE) continue;

            int sqIdx = sq.ordinal();
            if (isBlack) {
                sqIdx = 63 - sqIdx; // Perspective flip
            }

            boolean isMyPiece = isBlack ? (piece.getPieceSide() == Side.BLACK)
                                        : (piece.getPieceSide() == Side.WHITE);

            int pieceTypeIdx = getPieceTypeIndex(piece);
            int planeIdx = isMyPiece ? pieceTypeIdx : (6 + pieceTypeIdx);

            vec[planeIdx * 64 + sqIdx] = 1.0f;
        }
    }

    private static void encodeReachability(Board board, float[] vec, boolean isBlack) {
        // Active Player Legal Targets (Planes 12-17)
        List<Move> legalMoves = board.legalMoves();
        for (Move move : legalMoves) {
            Piece piece = board.getPiece(move.getFrom());
            if (piece == Piece.NONE) continue;
            int pieceTypeIdx = getPieceTypeIndex(piece);
            int planeIdx = 12 + pieceTypeIdx;

            int toSq = move.getTo().ordinal();
            if (isBlack) toSq = 63 - toSq;

            vec[planeIdx * 64 + toSq] = 1.0f;
        }

        // Opponent Attack Map / Reachability (Planes 18-23)
        Board clone = board.clone();
        clone.setSideToMove(board.getSideToMove().flip());
        List<Move> oppMoves = clone.legalMoves();
        for (Move move : oppMoves) {
            Piece piece = clone.getPiece(move.getFrom());
            if (piece == Piece.NONE) continue;
            int pieceTypeIdx = getPieceTypeIndex(piece);
            int planeIdx = 18 + pieceTypeIdx;

            int toSq = move.getTo().ordinal();
            if (isBlack) toSq = 63 - toSq; // Maintain active perspective

            vec[planeIdx * 64 + toSq] = 1.0f;
        }
    }

    private static void encodeAuxiliary(Board board, float[] vec, boolean isBlack) {
        int offset = 1536;

        // Original Side Color (1 feature): 1.0 for White, 0.0 for Black
        vec[offset++] = (board.getSideToMove() == Side.WHITE) ? 1.0f : 0.0f;

        // Castling Rights (4 features): Active King, Active Queen, Opponent King, Opponent Queen
        Side activeSide = isBlack ? Side.BLACK : Side.WHITE;
        Side oppSide = isBlack ? Side.WHITE : Side.BLACK;

        vec[offset++] = hasKingSideCastle(board, activeSide) ? 1.0f : 0.0f;
        vec[offset++] = hasQueenSideCastle(board, activeSide) ? 1.0f : 0.0f;
        vec[offset++] = hasKingSideCastle(board, oppSide) ? 1.0f : 0.0f;
        vec[offset++] = hasQueenSideCastle(board, oppSide) ? 1.0f : 0.0f;

        // En Passant Target Square (64 features)
        Square epSq = board.getEnPassant();
        if (epSq != null && epSq != Square.NONE) {
            int epIdx = epSq.ordinal();
            if (isBlack) epIdx = 63 - epIdx;
            vec[offset + epIdx] = 1.0f;
        }
        offset += 64;

        // Halfmove Clock (1 feature)
        vec[offset++] = Math.min((board.getHalfMoveCounter() != null ? board.getHalfMoveCounter() : 0) / 100.0f, 1.0f);

        // Fullmove Progress (1 feature)
        vec[offset++] = Math.min((board.getMoveCounter() != null ? board.getMoveCounter() : 1) / 100.0f, 1.0f);

        // Active King in Check (1 feature)
        vec[offset++] = (board.isMated() || board.isKingAttacked()) ? 1.0f : 0.0f;

        // Opponent King in Check (1 feature)
        Board clone = board.clone();
        clone.setSideToMove(board.getSideToMove().flip());
        vec[offset++] = clone.isKingAttacked() ? 1.0f : 0.0f;
    }

    private static boolean hasKingSideCastle(Board board, Side side) {
        CastleRight cr = board.getCastleRight(side);
        return cr == CastleRight.KING_SIDE || cr == CastleRight.KING_AND_QUEEN_SIDE;
    }

    private static boolean hasQueenSideCastle(Board board, Side side) {
        CastleRight cr = board.getCastleRight(side);
        return cr == CastleRight.QUEEN_SIDE || cr == CastleRight.KING_AND_QUEEN_SIDE;
    }

    private static int getPieceTypeIndex(Piece piece) {
        switch (piece.getPieceType()) {
            case PAWN:   return 0;
            case KNIGHT: return 1;
            case BISHOP: return 2;
            case ROOK:   return 3;
            case QUEEN:  return 4;
            case KING:   return 5;
            default: throw new IllegalArgumentException("Invalid piece: " + piece);
        }
    }
}
