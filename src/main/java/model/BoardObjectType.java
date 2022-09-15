package model;

public enum BoardObjectType {
    P(true),
    W(true),
    M(true),
    G(true),
    E(true),
    R(false),
    B(false);

    BoardObjectType(boolean garbage) {
        this.garbage = garbage;
    }

    private boolean garbage;

    public boolean isGarbage() {
        return this.garbage;
    }
}
