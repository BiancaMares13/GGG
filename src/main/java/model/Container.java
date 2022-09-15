package model;

public class Container {
    private BoardObjectType boardObjectType;
    private Integer remainingSpace;

    public Container() {
    }

    public Container(Integer remainingSpace) {
        this.remainingSpace = remainingSpace;
    }

    public BoardObjectType getGarbageType() {
        return boardObjectType;
    }

    public void setGarbageType(BoardObjectType boardObjectType) {
        this.boardObjectType = boardObjectType;
    }

    public Integer getRemainingSpace() {
        return remainingSpace;
    }

    public void setRemainingSpace(Integer remainingSpace) {
        this.remainingSpace = remainingSpace;
    }
}
