package upf.at.ban.api;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.GET;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import upf.at.ban.model.ClientProfile;
import upf.at.ban.model.Station;
import upf.at.ban.store.ClientStore;
import upf.at.ban.service.StationCacheService;

import javax.ws.rs.QueryParam;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import javax.ws.rs.client.WebTarget;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path("/notifier")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotifierResource {

    // New endpoint to notify all clients about free slots
    @POST
    @Path("/slots")
    public Response notifySlots() {

        List<Station> stations = StationCacheService.getStations();

        List<Map<String, Object>> notifications = new ArrayList<>();

        for (ClientProfile client : ClientStore.list()) {

            StringBuilder message = new StringBuilder("Bicing free slots:\n");

            Map<String, Object> clientResult = new HashMap<>();
            clientResult.put("phone", client.phone);

            List<Map<String, Object>> stationResults = new ArrayList<>();

            for (Integer stationId : client.stationIds) {

                Station s = stations.stream()
                                    .filter(st -> st.id == stationId)
                                    .findFirst()
                                    .orElse(null);

                Map<String, Object> stationMap = new HashMap<>();
                stationMap.put("stationId", stationId);

                if (s != null) {
                    message.append("Station ").append(s.id)
                        .append(": ").append(s.freeSlots)
                        .append(" free slots\n");

                    stationMap.put("freeSlots", s.freeSlots);
                } else {
                    message.append("Station ").append(stationId)
                        .append(": not found\n");

                    stationMap.put("error", "not found");
                }

                stationResults.add(stationMap);
            }

            // Send Telegram message
            sendTelegram(client.telegramToken, client.telegramChatId, message.toString());

            clientResult.put("stations", stationResults);
            notifications.add(clientResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("notifications", notifications);

        return Response.ok(response).build();
    }

    // Send air quality for a client
    @GET
    @Path("/airquality")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response notifyAirQuality(@QueryParam("phone") String phone) {
        try {
            String clientIp = getClientIp();
            String city = getCityFromIP(clientIp);
            int aqi = getAQIForCity(city);
            String aqiText = aqiToText(aqi);

            ClientProfile client = ClientStore.get(phone);
            if (client == null) {
                return Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"client not found\"}")
                            .build();
            }

            sendTelegram(client.telegramToken, client.telegramChatId,
                        "Air quality in " + city + ": " + aqi + " (" + aqiText + ")");

            // Build JSON response
            Map<String, Object> result = new HashMap<>();
            result.put("phone", phone);
            result.put("city", city);
            result.put("aqi", aqi);
            result.put("description", aqiText);

            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"error\":\"" + e.getMessage() + "\"}")
                        .build();
        }
    }

    private static final String AQI_API_BASE = "https://api.waqi.info/feed";
    private static final String AQI_API_TOKEN = "40d4f67b59e39fd2c4e40caabab3bd244fd9ea54"; // your token from aqicn.org

    public int getAQIForCity(String city) throws Exception {

        if (city == null || city.isEmpty()) {
            throw new Exception("City is null");
        }

        Client client = ClientBuilder.newClient();

        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.toString());

        String url = AQI_API_BASE + "/" + encodedCity + "/?token=" + AQI_API_TOKEN;

        WebTarget target = client.target(url);

        String responseBody = target.request(MediaType.APPLICATION_JSON_TYPE)
                                    .get(String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);

        if (!root.path("status").asText().equals("ok")) {
            throw new Exception("AQI API error: " + root.path("data").asText());
        }

        return root.path("data").path("aqi").asInt();
    }

    private String aqiToText(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very Unhealthy";
        return "Hazardous";
    }

    // Helper method to send Telegram message
    private void sendTelegram(String token, String chatId, String text) {

        Client client = ClientBuilder.newClient();

        String url = "https://api.telegram.org/bot" + token + "/sendMessage";

        client.target(url)
            .queryParam("chat_id", chatId)
            .queryParam("text", text)
            .request()
            .get();
    }

    private String getCityFromIP(String ip) {
        // For testing we hardcode barcelona since running the app in localhost won't find us a valid city with our IP (that being localhost).
        return "barcelona";
        /*try {
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target("http://ip-api.com/json/" + ip);
            String response = target.request().get(String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if (root.get("status").asText().equals("success")) {
                return root.get("city").asText();
            } else {
                System.err.println("IP API failed: " + root.get("message").asText());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }*/
    }

    @Context
    private HttpServletRequest request;
    // To get client IP for AQI notifications
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For"); // in case behind a proxy
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }


    // Simple DTO for Telegram API
    public static class TelegramMessage {
        public String chat_id;
        public String text;

        public TelegramMessage(String chat_id, String text) {
            this.chat_id = chat_id;
            this.text = text;
        }
    }
    @POST
    @Path("/test")
    public Response testTelegram() {

        String token = "YOUR_BOT_TOKEN";
        String chatId = "YOUR_CHAT_ID";

        sendTelegram(token, chatId, "Hello from BAN server 🚀");

        return Response.ok("{\"status\":\"sent\"}").build();
    }
}