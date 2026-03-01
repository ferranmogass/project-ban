package upf.at.ban.model;

import java.util.List;

public class Data {
    private List<Station> stations;

    public Data() {}

    public Data(List<Station> stations) {
        this.stations = stations;
    }

    public List<Station> getStations() { return stations; }
    public void setStations(List<Station> stations) { this.stations = stations; }

    @Override
    public String toString() {
        return "Data{" + "stations=" + stations + '}';
    }
}