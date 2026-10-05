package com.chessmind.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Client for communicating with the Stockfish chess engine over the Universal Chess Interface (UCI) protocol.
 */
public class StockfishClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StockfishClient.class);

    private final String binaryPath;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public record EngineAnalysis(String bestMove, Integer scoreCp, Integer mateIn) {}

    public StockfishClient() {
        this("stockfish");
    }

    public StockfishClient(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public synchronized boolean start() {
        try {
            File binFile = new File(binaryPath);
            ProcessBuilder pb = binFile.exists()
                    ? new ProcessBuilder(binFile.getAbsolutePath())
                    : new ProcessBuilder(binaryPath);

            pb.redirectErrorStream(true);
            this.process = pb.start();
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            sendCommand("uci");
            String line;
            while ((line = reader.readLine()) != null) {
                if ("uciok".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }
            sendCommand("isready");
            while ((line = reader.readLine()) != null) {
                if ("readyok".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("Could not start Stockfish at path '{}': {}", binaryPath, e.getMessage());
            return false;
        }
    }

    public synchronized void sendCommand(String command) throws IOException {
        if (writer == null) {
            throw new IOException("Stockfish process is not started.");
        }
        writer.write(command + "\n");
        writer.flush();
    }

    public synchronized EngineAnalysis analyzeFen(String fen, int depth) throws IOException {
        if (process == null || !process.isAlive()) {
            if (!start()) {
                throw new IOException("Stockfish process is unavailable.");
            }
        }

        sendCommand("position fen " + fen);
        sendCommand("go depth " + depth);

        String bestMove = null;
        Integer scoreCp = null;
        Integer mateIn = null;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("info ")) {
                if (line.contains("score cp ")) {
                    int idx = line.indexOf("score cp ");
                    String[] parts = line.substring(idx + 9).split("\\s+");
                    if (parts.length > 0) {
                        try {
                            scoreCp = Integer.parseInt(parts[0]);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (line.contains("score mate ")) {
                    int idx = line.indexOf("score mate ");
                    String[] parts = line.substring(idx + 11).split("\\s+");
                    if (parts.length > 0) {
                        try {
                            mateIn = Integer.parseInt(parts[0]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (line.startsWith("bestmove ")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    bestMove = parts[1];
                }
                break;
            }
        }

        return new EngineAnalysis(bestMove, scoreCp, mateIn);
    }

    public boolean isAvailable() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (process != null) {
            try {
                if (process.isAlive()) {
                    sendCommand("quit");
                    process.waitFor();
                }
            } catch (Exception ignored) {
            } finally {
                process.destroy();
                process = null;
                reader = null;
                writer = null;
            }
        }
    }
}
