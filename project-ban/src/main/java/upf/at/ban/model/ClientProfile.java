package upf.at.ban.model;

import java.util.List;

public class ClientProfile {
    public String phone;
    public List<Integer> stationIds;

    // Per més endavant poder enviar notificacions per Telegram
    public String telegramToken;
    public String telegramChatId;

    // Per que Jackson pugui deserialitzar el JSON en un objecte Java, necessita un constructor sense arguments
    public ClientProfile() {}

    public ClientProfile(String phone, List<Integer> stationIds, String telegramToken, String telegramChatId) {
        this.phone = phone;
        this.stationIds = stationIds;
        this.telegramToken = telegramToken;
        this.telegramChatId = telegramChatId;
    }
}