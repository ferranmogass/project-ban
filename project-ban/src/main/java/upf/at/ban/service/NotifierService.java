package upf.at.ban.service;

import java.util.List;

import upf.at.ban.model.ClientProfile;
import upf.at.ban.model.Station;
import upf.at.ban.store.ClientStore;

public class NotifierService {

    /**
     * Sends a notification with free slots of the stations the user is subscribed to.
     */
    public static void notifySlots(String phone) {
        ClientProfile client = ClientStore.get(phone); // use existing get()
        if (client == null) {
            System.out.println("Client not found: " + phone);
            return;
        }

        List<Integer> stationsIds = client.stationIds;
        List<Station> allStations = upf.at.ban.service.StationCacheService.getStations();

        StringBuilder message = new StringBuilder("Bicing Slots:\n");
        for (Integer id : stationsIds) {
            for (Station s : allStations) {
                if (s.id == id) {
                    message.append("Station ").append(s.id)
                           .append(": ").append(s.freeSlots)
                           .append(" free bikes\n");
                }
            }
        }

        // Dummy Telegram send (for now)
        System.out.println("Sending to " + client.telegramChatId + ": " + message.toString());
    }

    /**
     * Sends a notification with the Air Quality Index for the user's city.
     * Currently uses a dummy AQI value.
     */
    public static void notifyAirQuality(String ip, String phone) {
        ClientProfile client = ClientStore.get(phone);
        if (client == null) {
            System.out.println("Client not found: " + phone);
            return;
        }

        // Dummy IP → City mapping
        String city = "Barcelona"; // Replace with real IP→city API later
        int aqi = 42; // Replace with real AQI API later

        String message = String.format("Air Quality in %s: AQI=%d", city, aqi);

        // Dummy Telegram send
        System.out.println("Sending to " + client.telegramChatId + ": " + message);
    }
}