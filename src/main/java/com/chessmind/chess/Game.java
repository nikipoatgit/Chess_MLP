package com.chessmind.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import com.github.bhlangonijr.chesslib.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State manager wrapping the Chesslib Board.
 * Handles move execution, legal move queries, auto-queen promotions, and game status.
 */
public class Game {

    private final Board board;
    private final List<String> moveHistorySan;
    private final List<Move> moveHistory;

    public Game() {
        this.board = new Board();
        this.moveHistorySan = new ArrayList<>();
        this.moveHistory = new ArrayList<>();
    }

    public synchronized boolean makeMove(Square from, Square to) {
        Piece piece = board.getPiece(from);
        if (piece == Piece.NONE) return false;

        // Auto-promote to Queen when a Pawn moves to the final rank (rank 8 for white, rank 1 for black)
        Piece promotion = Piece.NONE;
        if (piece.getPieceType() == PieceType.PAWN) {
            int toRank = to.getRank().ordinal();
            if ((piece.getPieceSide() == Side.WHITE && toRank == 7) ||
                (piece.getPieceSide() == Side.BLACK && toRank == 0)) {
                promotion = (piece.getPieceSide() == Side.WHITE) ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
            }
        }

        Move move = (promotion != Piece.NONE) ? new Move(from, to, promotion) : new Move(from, to);
        return makeMove(move);
    }

    public synchronized boolean makeMove(Move move) {
        if (!board.isMoveLegal(move, true)) {
            // Also check if move was requested without promotion piece when promotion was required
            if (move.getPromotion() == Piece.NONE) {
                Piece piece = board.getPiece(move.getFrom());
                if (piece != Piece.NONE && piece.getPieceType() == PieceType.PAWN) {
                    Piece promo = (piece.getPieceSide() == Side.WHITE) ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
                    Move promoMove = new Move(move.getFrom(), move.getTo(), promo);
                    if (board.isMoveLegal(promoMove, true)) {
                        move = promoMove;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        String san = move.getSan();
        boolean success = board.doMove(move);
        if (success) {
            moveHistory.add(move);
            moveHistorySan.add(san != null && !san.isEmpty() ? san : move.toString());
        }
        return success;
    }

    public synchronized Move undoMove() {
        if (moveHistory.isEmpty()) return null;
        Move undone = board.undoMove();
        if (!moveHistory.isEmpty()) {
            moveHistory.remove(moveHistory.size() - 1);
        }
        if (!moveHistorySan.isEmpty()) {
            moveHistorySan.remove(moveHistorySan.size() - 1);
        }
        return undone;
    }

    public synchronized void reset() {
        board.loadFromFen(Constants.startStandardFENPosition);
        moveHistory.clear();
        moveHistorySan.clear();
    }

    public synchronized void loadFen(String fen) {
        board.loadFromFen(fen);
        moveHistory.clear();
        moveHistorySan.clear();
    }

    public Board getBoard() {
        return board;
    }

    /** Returns a stable board snapshot for work performed off the UI thread. */
    public synchronized Board copyBoard() {
        return board.clone();
    }

    public Side getSideToMove() {
        return board.getSideToMove();
    }

    public List<Move> getLegalMoves() {
        return board.legalMoves();
    }

    public List<Move> getLegalMovesFrom(Square from) {
        List<Move> legal = board.legalMoves();
        List<Move> fromMoves = new ArrayList<>();
        for (Move m : legal) {
            if (m.getFrom() == from) {
                fromMoves.add(m);
            }
        }
        return fromMoves;
    }

    public boolean isGameOver() {
        return board.isMated() || board.isDraw() || board.isStaleMate() || board.isInsufficientMaterial();
    }

    public String getStatusMessage() {
        if (board.isMated()) {
            Side winner = board.getSideToMove().flip();
            return "Checkmate! " + winner + " wins!";
        }
        if (board.isStaleMate()) {
            return "Draw by Stalemate!";
        }
        if (board.isDraw()) {
            if (board.isInsufficientMaterial()) {
                return "Draw by Insufficient Material!";
            }
            if (board.isRepetition()) {
                return "Draw by Repetition!";
            }
            return "Draw!";
        }
        if (board.isKingAttacked()) {
            return board.getSideToMove() + " is in Check!";
        }
        return board.getSideToMove() + " to move";
    }

    public List<String> getMoveHistorySan() {
        return Collections.unmodifiableList(moveHistorySan);
    }

    public List<Move> getMoveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }
}
