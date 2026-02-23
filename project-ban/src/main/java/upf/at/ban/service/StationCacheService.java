package upf.at.ban.service;

import java.util.List;

import upf.at.ban.model.Station;

public class StationCacheService {

    private static final long TTL_MS = 120_000;  

    //Quan s'ha omplert el cache per última vegada.
    private static long lastFetchMs = 0;

    //Llista de les estacions. 
    private static List<Station> cached = null;

    //Vigilar de no tenir 2 threads entrant alhora.
    public static synchronized List<Station> getStations() {
        long now = System.currentTimeMillis();

        //Si el cache existeix i no ha caducat, el retornem.
        if (cached != null && (now - lastFetchMs) < TTL_MS) {
            return cached; 
        }

        // Cridarem l’API real de Bicing.

        // Inventat
        cached = List.of(
                new Station(1, "Station 1", 10),
                new Station(2, "Station 2", 3),
                new Station(3, "Station 3", 0)
        );
        
        lastFetchMs = now;
        return cached;
    }
}