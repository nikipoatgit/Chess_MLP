package com.chessmind.ui;

import com.chessmind.chess.Game;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 8x8 Chessboard component with click-to-move, square highlighting, and board flipping.
 */
public class BoardView extends GridPane {

    private static final int SQUARE_SIZE = 72;

    private static final String LIGHT_SQUARE_COLOR = "#EBECD0";
    private static final String DARK_SQUARE_COLOR = "#779556";
    private static final String SELECTED_COLOR = "#F7D560";
    private static final String LAST_MOVE_COLOR = "#BACA44";

    private final Game game;
    private boolean flipped = false;
    private Square selectedSquare = null;
    private final Set<Square> legalTargets = new HashSet<>();
    private Move lastMove = null;

    private BiConsumer<Square, Square> onMoveMadeListener;

    private final Map<Square, StackPane> squarePanes = new HashMap<>();
    private final Map<Square, Label> pieceLabels = new HashMap<>();
    private final Map<Square, Circle> targetDots = new HashMap<>();

    public BoardView(Game game) {
        this.game = game;
        setAlignment(Pos.CENTER);
        getStyleClass().add("board-view");
        buildGrid();
        updateBoard();
    }

    public void setOnMoveMade(BiConsumer<Square, Square> listener) {
        this.onMoveMadeListener = listener;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        buildGrid();
        updateBoard();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void toggleFlip() {
        setFlipped(!flipped);
    }

    public void setLastMove(Move move) {
        this.lastMove = move;
        refreshSquareStyles();
    }

    private void buildGrid() {
        getChildren().clear();
        squarePanes.clear();
        pieceLabels.clear();
        targetDots.clear();

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int displayRow = flipped ? rank : (7 - rank);
                int displayCol = flipped ? (7 - file) : file;

                Square sq = Square.squareAt((rank * 8) + file);

                StackPane pane = new StackPane();
                pane.setPrefSize(SQUARE_SIZE, SQUARE_SIZE);
                pane.setMinSize(SQUARE_SIZE, SQUARE_SIZE);
                pane.setMaxSize(SQUARE_SIZE, SQUARE_SIZE);

                Label pieceLabel = new Label();
                pieceLabel.getStyleClass().add("piece-label");

                Circle dot = new Circle(SQUARE_SIZE * 0.16);
                dot.setFill(Color.web("#000000", 0.25));
                dot.setVisible(false);

                pane.getChildren().addAll(dot, pieceLabel);
                pane.setOnMouseClicked(e -> handleSquareClick(sq));

                squarePanes.put(sq, pane);
                pieceLabels.put(sq, pieceLabel);
                targetDots.put(sq, dot);

                add(pane, displayCol, displayRow);
            }
        }
        refreshSquareStyles();
    }

    private void handleSquareClick(Square sq) {
        if (selectedSquare == null) {
            // Select piece if it belongs to the active player
            Piece piece = game.getBoard().getPiece(sq);
            if (piece != Piece.NONE && piece.getPieceSide() == game.getSideToMove()) {
                selectedSquare = sq;
                legalTargets.clear();
                List<Move> legal = game.getLegalMovesFrom(sq);
                for (Move m : legal) {
                    legalTargets.add(m.getTo());
                }
                refreshSquareStyles();
            }
        } else {
            // Target square chosen
            if (legalTargets.contains(sq)) {
                Square from = selectedSquare;
                selectedSquare = null;
                legalTargets.clear();
                refreshSquareStyles();

                if (onMoveMadeListener != null) {
                    onMoveMadeListener.accept(from, sq);
                }
            } else {
                // If another piece of same side clicked, select that instead
                Piece piece = game.getBoard().getPiece(sq);
                if (piece != Piece.NONE && piece.getPieceSide() == game.getSideToMove()) {
                    selectedSquare = sq;
                    legalTargets.clear();
                    List<Move> legal = game.getLegalMovesFrom(sq);
                    for (Move m : legal) {
                        legalTargets.add(m.getTo());
                    }
                } else {
                    selectedSquare = null;
                    legalTargets.clear();
                }
                refreshSquareStyles();
            }
        }
    }

    public void updateBoard() {
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            Label label = pieceLabels.get(sq);
            if (label == null) continue;

            Piece piece = game.getBoard().getPiece(sq);
            label.setText(getPieceSymbol(piece));

            if (piece != Piece.NONE) {
                if (piece.getPieceSide() == Side.WHITE) {
                    label.setStyle("-fx-text-fill: #ffffff; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 2, 0.5, 0, 1);");
                } else {
                    label.setStyle("-fx-text-fill: #222222; -fx-effect: dropshadow(gaussian, rgba(255,255,255,0.4), 1, 0.5, 0, 1);");
                }
            }
        }
        refreshSquareStyles();
    }

    private void refreshSquareStyles() {
        for (Map.Entry<Square, StackPane> entry : squarePanes.entrySet()) {
            Square sq = entry.getKey();
            StackPane pane = entry.getValue();

            boolean isLight = sq.isLightSquare();
            String bg = isLight ? LIGHT_SQUARE_COLOR : DARK_SQUARE_COLOR;

            if (selectedSquare == sq) {
                bg = SELECTED_COLOR;
            } else if (lastMove != null && (lastMove.getFrom() == sq || lastMove.getTo() == sq)) {
                bg = LAST_MOVE_COLOR;
            }

            pane.setStyle("-fx-background-color: " + bg + ";");

            Circle dot = targetDots.get(sq);
            if (dot != null) {
                dot.setVisible(legalTargets.contains(sq));
            }
        }
    }

    private static String getPieceSymbol(Piece piece) {
        return switch (piece) {
            case WHITE_PAWN -> "♙";
            case WHITE_KNIGHT -> "♘";
            case WHITE_BISHOP -> "♗";
            case WHITE_ROOK -> "♖";
            case WHITE_QUEEN -> "♕";
            case WHITE_KING -> "♔";
            case BLACK_PAWN -> "♟";
            case BLACK_KNIGHT -> "♞";
            case BLACK_BISHOP -> "♝";
            case BLACK_ROOK -> "♜";
            case BLACK_QUEEN -> "♛";
            case BLACK_KING -> "♚";
            case NONE -> "";
        };
    }
}
