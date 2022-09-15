package model;

import java.util.concurrent.atomic.AtomicInteger;

public class BoardObject {

    public int row;

    public int col;

    public int volume;

    public BoardObject(AtomicInteger i, AtomicInteger j) {
        this.row = i.get();
        this.col = j.get();
    }

    public BoardObject(int row, int col) {
        this.row = row;
        this.col = col;
    }
}
