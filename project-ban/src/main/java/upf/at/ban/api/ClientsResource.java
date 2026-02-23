package upf.at.ban.api;

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import upf.at.ban.model.ClientProfile;
import upf.at.ban.store.ClientStore;

@Path("/clients")
@Produces(MediaType.APPLICATION_JSON)
public class ClientsResource {

    @POST
    @Path("/subscribe")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response subscribe(ClientProfile client) {
        //Gestió d'errors bàsica
        
        if (client == null || client.phone == null || client.phone.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"phone is required\"}")
                    .build();
        }
        
        if (client.stationIds == null || client.stationIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"stationIds is required\"}")
                    .build();
        }

        // Afegir Verify Age.

        ClientStore.save(client);

        return Response.ok(client).build();
    }

    @GET
    public Collection<ClientProfile> getClients() {
        return ClientStore.list();
    }
}