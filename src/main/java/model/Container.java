package model;

public class Container {
    private BoardObjectType type;
    private Integer volume;

    public Container() {
    }

    public BoardObjectType getType() {
        return type;
    }

    public void setType(BoardObjectType type) {
        this.type = type;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }
}
