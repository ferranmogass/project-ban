package upf.at.ban.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            String bicingURLPos = "https://opendata-ajuntament.barcelona.cat/data/dataset/bd2462df-6e1e-4e37-8205-a4b8e7313b84/resource/f60e9291-5aaa-417d-9b91-612a9de800aa/download";
            String token = "de31ea66e54f7b970b7f61941d57b3aa9d7358f2081d129e61fb2fc4f262e5e2";

            Client client = ClientBuilder.newClient();

            // GET JSON as string
            String response = client.target(bicingURL)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)  // no "Bearer"
                    .get(String.class);
            
            String responsePos = client.target(bicingURLPos)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)  // no "Bearer"
                    .get(String.class);

            // Debug print
            System.out.println("Bicing API response (truncated):");
            System.out.println(response.substring(0, Math.min(1000, response.length())) + " ...");
            System.out.println(responsePos.substring(0, Math.min(1000, responsePos.length())) + " ...");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode statusStations = mapper.readTree(response).path("data").path("stations");
            JsonNode infoStations = mapper.readTree(responsePos).path("data").path("stations");

            // Build a map: station_id -> infoNode (lat/lon)
            Map<Integer, JsonNode> infoMap = new HashMap<>();
            for (JsonNode infoNode : infoStations) {
                int id = infoNode.path("station_id").asInt();
                infoMap.put(id, infoNode);
            }

            List<Station> stations = new ArrayList<>();

            // Merge while iterating over status stations
            for (JsonNode statusNode : statusStations) {
                int id = statusNode.path("station_id").asInt();
                String name = statusNode.has("name") ? statusNode.get("name").asText() : "Station " + id;
                int freeSlots = statusNode.path("num_bikes_available").asInt();
                int numDocks = statusNode.path("num_docks_available").asInt();

                // Get lat/lon from infoMap
                double lat = 0.0;
                double lon = 0.0;
                if (infoMap.containsKey(id)) {
                    JsonNode infoNode = infoMap.get(id);
                    lat = infoNode.path("lat").asDouble(0.0);
                    lon = infoNode.path("lon").asDouble(0.0);
                }
                
                // Create Station object and add to list
                Station station = new Station(id, name, freeSlots, numDocks);
                station.lat = lat;
                station.lon = lon;

                stations.add(station);
            }

            System.out.println("Fetched " + stations.size() + " stations from Bicing API");
            return stations;

        } catch (Exception e) {
            System.err.println("Error fetching stations: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    public static double fetchTemperature(double lat, double lon) {
        try {
            Client client = ClientBuilder.newClient();
            String url = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true",
                    lat, lon
            );

            String response = client.target(url)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode currentWeather = root.path("current_weather");
            if (currentWeather.isMissingNode()) return Double.NaN;

            return currentWeather.path("temperature").asDouble(Double.NaN);

        } catch (Exception e) {
            System.err.println("Error fetching temperature for lat=" + lat + ", lon=" + lon + ": " + e.getMessage());
            return Double.NaN;
        }
    }
}