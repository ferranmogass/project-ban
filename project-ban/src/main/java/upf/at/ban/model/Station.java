package upf.at.ban.model;

public class Station {
    public int id;
    public String name;
    public int freeSlots;       // num_bikes_available
    public int numDocks;        // num_docks_available

    public Station() {}

    public Station(int id, String name, int freeSlots, int numDocks) {
        this.id = id;
        this.name = name;
        this.freeSlots = freeSlots;
        this.numDocks = numDocks;
    }
}