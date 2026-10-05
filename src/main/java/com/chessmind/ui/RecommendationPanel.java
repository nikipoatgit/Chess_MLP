package com.chessmind.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Minimal controls for an automated MLP versus Stockfish game. */
public class RecommendationPanel extends VBox {

    private final Label statusLabel;
    private final Button playButton;
    private final Button resetButton;
    private final ComboBox<String> stockfishModeBox;

    public RecommendationPanel() {
        setSpacing(14);
        setPadding(new Insets(16));
        setPrefWidth(320);
        setMinWidth(280);
        getStyleClass().add("recommendation-panel");

        Label title = new Label("ChessMind AI");
        title.getStyleClass().add("panel-title");

        statusLabel = new Label("White to move");
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setWrapText(true);

        playButton = new Button("Play");
        playButton.getStyleClass().addAll("btn", "btn-primary", "btn-lg");
        playButton.setMaxWidth(Double.MAX_VALUE);

        resetButton = new Button("Reset game");
        resetButton.getStyleClass().addAll("btn", "btn-secondary");
        resetButton.setMaxWidth(Double.MAX_VALUE);

        Label stockfishModeLabel = new Label("Stockfish mode");
        stockfishModeLabel.getStyleClass().add("field-label");
        stockfishModeBox = new ComboBox<>();
        stockfishModeBox.getItems().addAll("Easy", "Medium", "Hard");
        stockfishModeBox.setValue("Medium");
        stockfishModeBox.setMaxWidth(Double.MAX_VALUE);
        stockfishModeBox.setTooltip(new javafx.scene.control.Tooltip(
            "Controls Stockfish search depth: Easy 2, Medium 8, Hard 16."));

        getChildren().addAll(title, statusLabel, stockfishModeLabel, stockfishModeBox, playButton, resetButton);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public Button getPlayButton() {
        return playButton;
    }

    public Button getResetButton() {
        return resetButton;
    }

    public ComboBox<String> getStockfishModeBox() {
        return stockfishModeBox;
    }

    public int getStockfishDepth() {
        return switch (stockfishModeBox.getValue()) {
            case "Easy" -> 2;
            case "Hard" -> 16;
            default -> 8;
        };
    }
}
