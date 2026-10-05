# ChessMLP Weights Context

## Important boundary

This repository does not train a model or generate trained weights. The JavaFX application only loads an already-trained DL4J model at startup. Training must happen externally, currently in Google Colab, and the exported archive must then be copied into this project.

The runtime file is:

```text
model/weights.zip
```

The path is relative to the directory from which the application is started. Start the application from the repository root so it resolves to `ChessMLP/model/weights.zip`.

The current workspace contains this archive at `model/weights.zip`.

## Quick start

From the repository root:

```bash
./gradlew test
./gradlew run
```

The application loads the weights for the MLP side of the automatic game. The
MLP plays White, Stockfish plays Black, and the selected Stockfish level controls
the engine search depth. The archive is read only; the application never trains,
updates, or replaces it.

## How to generate the weights

### 1. Prepare the training data externally

Use the same board encoding and move-index convention as the Java application:

- Input: 1,609 features produced by `BoardEncoder`.
- Output class: `fromSquare * 64 + toSquare`.
- For black positions, the application mirrors both square ordinals with `63 - ordinal` before computing the class index.
- The output space has 4,096 classes, including moves that are illegal in a particular position.

The model should be trained to predict the target move class from the encoded board. The application filters the model output to legal moves at inference time, so training data must use the same orientation and indexing rules.

### 2. Build and train the matching DL4J model in Colab

The exported object must be a DL4J `MultiLayerNetwork` with this contract:

```text
Input:  1,609 float features
Dense:  1,609 -> 1,024
BatchNorm + ReLU
Dense:  1,024 -> 512
BatchNorm + ReLU
Dense:  512 -> 256
BatchNorm + ReLU
Output: 256 -> 4,096 softmax classes
```

The final model must contain trained parameters. Do not export an uninitialized network or a Python/PyTorch checkpoint; the Java application restores the archive with DL4J's `ModelSerializer`.

### 3. Export one DL4J archive

After training in Colab, export the trained network:

```java
File output = new File("weights.zip");
ModelSerializer.writeModel(network, output, true);
```

Download that `weights.zip` and install it from the repository root:

```bash
mkdir -p model
cp /path/to/weights.zip model/weights.zip
```

The third argument must be `true` so updater state is included in the archive. The application does not continue training, but this keeps the archive in the expected DL4J format.

## Stockfish prerequisite

The weights archive only provides the MLP. The opponent is a separate Stockfish
executable. The application looks for `stockfish` on `PATH` by default, or uses
the path supplied through the `chessmind.stockfish.path` system property.

On Debian or Ubuntu, install it with:

```bash
sudo apt update
sudo apt install stockfish
```

Then verify it and start the application from the repository root:

```bash
stockfish
./gradlew run
```

If Stockfish is stored elsewhere, pass its executable path:

```bash
./gradlew run -Dchessmind.stockfish.path=/absolute/path/to/stockfish
```

The warning `Could not start Stockfish at path 'stockfish'` means the model
loaded successfully but the engine executable could not be started. It is not a
weights-format error.

## Validate the installation

Run the existing tests and launch the app from the repository root:

```bash
./gradlew test
./gradlew run
```

On successful startup, the app logs:

```text
Loaded model weights from model/weights.zip
```

The application fails at startup when the file is missing, unreadable, not a DL4J `MultiLayerNetwork`, or incompatible with the expected input/output contract.

To run the generated distribution instead:

```bash
./build/scripts/ChessMLP
```

## Common mistakes

- Naming the file `weights.h5`, `model.bin`, or another format instead of `model/weights.zip`.
- Copying the archive into `build/` or `src/`; the runtime path is `model/weights.zip`.
- Exporting a model with an input size other than 1,609 or output size other than 4,096.
- Using a different board encoder, board orientation, or move-class mapping during training.
- Training a model with a different layer order or missing BatchNorm/ReLU blocks.
- Starting the app from a different working directory and therefore changing the meaning of the relative `model/weights.zip` path.
- Starting the app without installing Stockfish or without setting `chessmind.stockfish.path`.

## Source of truth

- Runtime path and architecture: `src/main/java/com/chessmind/ml/ChessModel.java`
- Startup loading: `src/main/java/com/chessmind/ui/ChessApp.java`
- Format summary: `WEIGHTS_FORMAT.md`
- Gradle dependencies: `build.gradle.kts`
