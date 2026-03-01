package upf.at.ban.service;

import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import upf.at.ban.model.Data;
import upf.at.ban.model.Station;

public class StationCacheService {

    // Cache duration: 120 seconds
    private static final long TTL_MS = 120_000;

    // Timestamp of last fetch
    private static long lastFetchMs = 0;

    // Cached list of stations
    private static List<Station> cached = null;

    /**
     * Returns the list of stations.
     * Uses cache if valid, otherwise calls Bicing API.
     */
    public static synchronized List<Station> getStations() {
        long now = System.currentTimeMillis();

        if (cached != null && (now - lastFetchMs) < TTL_MS) {
            System.out.println("Returning cached stations...");
            return cached;
        }

        System.out.println("Cache expired or empty, fetching from Bicing API...");
        cached = fetchFromBicing();
        lastFetchMs = now;

        return cached;
    }

    /**
     * Calls Bicing API and parses JSON into list of Station objects.
     */
    private static List<Station> fetchFromBicing() {
        try {
            String bicingURL = "https://opendata-ajuntament.barcelona.cat/data/dataset/6aa3416d-ce1a-494d-861b-7bd07f069600/resource/1b215493-9e63-4a12-8980-2d7e0fa19f85/download";
            String token = "YOUR_BICING_TOKEN"; // <-- replace with your actual token

            Client client = ClientBuilder.newClient();
            WebTarget target = client.target(bicingURL);

            // Parse directly into Data object, which contains List<Station>
            Data data = target.request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)
                    .get(new GenericType<Data>() {});

            // Return list of stations
            return data.getStations();

        } catch (Exception e) {
            System.err.println("Error fetching stations from Bicing API: " + e.getMessage());
            e.printStackTrace();

            // Return empty list if API fails
            return List.of();
        }
    }
}