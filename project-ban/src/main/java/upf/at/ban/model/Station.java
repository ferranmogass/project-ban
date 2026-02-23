package upf.at.ban.model;

public class Station {
    public int id;
    public String name;
    public int freeSlots;

    public Station() {}

    public Station(int id, String name, int freeSlots) {
        this.id = id;
        this.name = name;
        this.freeSlots = freeSlots;
    }
}