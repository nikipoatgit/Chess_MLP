# External Weights Format

The desktop app does not generate data or train. Train the model in Google Colab, then export one DL4J model archive.

## Required file

Place the exported file at:

```text
model/weights.zip
```

Export it from Colab with:

```java
File output = new File("weights.zip");
ModelSerializer.writeModel(network, output, true);
```

The archive must be a DL4J `MultiLayerNetwork` created with the same input/output contract:

```text
Input:  1,609 float features from BoardEncoder
Layers: Dense 1,609 -> 1,024
        BatchNorm + ReLU
        Dense 1,024 -> 512
        BatchNorm + ReLU
        Dense 512 -> 256
        BatchNorm + ReLU
Output: 4,096 softmax move classes
```

The 4,096 outputs represent `fromSquare * 64 + toSquare`. The app applies legal-move filtering before displaying recommendations.

The archive must contain trained parameters compatible with this architecture. The app loads it on startup and never creates a replacement model or updates the weights.

Stockfish is the game opponent. Its UI modes select UCI search depth 2 (Easy), 8 (Medium), or 16 (Hard). Stockfish itself is provided separately and selected with the `chessmind.stockfish.path` system property, defaulting to `stockfish` on `PATH`.
