package upf.at.ban.model;

public class Station {
    public int id;
    public String name;
    public int freeSlots;       // num_bikes_available
    public int numDocks;        // num_docks_available
    public double lat;   // Latitude of the station
    public double lon;   // Longitude of the station

    public Station() {}

    public Station(int id, String name, int freeSlots, int numDocks, double lat, double lon, double temperature) {
        this.id = id;
        this.name = name;
        this.freeSlots = freeSlots;
        this.numDocks = numDocks;
        this.lat = lat;
        this.lon = lon;
    }
    public Station(int id, String name, int freeSlots, int numDocks) {
        this.id = id;
        this.name = name;
        this.freeSlots = freeSlots;
        this.numDocks = numDocks;
        this.lat = 0;
        this.lon = 0;
    }
}