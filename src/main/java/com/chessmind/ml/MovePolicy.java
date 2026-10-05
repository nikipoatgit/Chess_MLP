package com.chessmind.ml;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;

@FunctionalInterface
public interface MovePolicy {
    Move chooseMove(Board board) throws IOException;
}
