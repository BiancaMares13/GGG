package model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public enum MoveType {
    up,
    down,
    left,
    right;
    private static final List<MoveType> VALUES =
            Collections.unmodifiableList(Arrays.asList(values()));
    private static final int SIZE = VALUES.size();
    private static final Random RANDOM = new Random();
    public static MoveType randomMove()  {
        return VALUES.get(RANDOM.nextInt(SIZE));
    }
}
