package upf.at.ban.store;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import upf.at.ban.model.ClientProfile;

//Guardar els clients en memòria (recuperar amb un GET)

public class ClientStore {
    private static final Map<String, ClientProfile> clients = new ConcurrentHashMap<>();

    public static void save(ClientProfile c) {
        //1 client per número de telèfon
        clients.put(c.phone, c);
    }

    public static Collection<ClientProfile> list() {
        return clients.values(); //Col·lecció de clients (sense clau)
    }

    //Recuperar un client pel seu número de telèfon
    public static ClientProfile get(String phone) {
        return clients.get(phone);
    }
};