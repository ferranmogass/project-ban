package upf.at.ban.service;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            String token = "de31ea66e54f7b970b7f61941d57b3aa9d7358f2081d129e61fb2fc4f262e5e2";

            Client client = ClientBuilder.newClient();

            // GET JSON as string
            String response = client.target(bicingURL)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)  // no "Bearer"
                    .get(String.class);

            // Debug print
            System.out.println("Bicing API response (truncated):");
            System.out.println(response.substring(0, Math.min(1000, response.length())) + " ...");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            List<Station> stations = new ArrayList<>();

            JsonNode stationsNode = root.path("data").path("stations");
            for (JsonNode node : stationsNode) {
                int id = node.path("station_id").asInt();
                String name = node.has("name") ? node.get("name").asText() : "Station " + id;
                int freeSlots = node.path("num_bikes_available").asInt();
                int numDocks = node.path("num_docks_available").asInt();

                stations.add(new Station(id, name, freeSlots, numDocks));
            }

            System.out.println("Fetched " + stations.size() + " stations from Bicing API");
            return stations;

        } catch (Exception e) {
            System.err.println("Error fetching stations: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
}