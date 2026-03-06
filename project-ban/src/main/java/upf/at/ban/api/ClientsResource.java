
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
import upf.at.ban.service.OpenGatewayAge;
import upf.at.ban.service.OpenGatewayAge.AgeCheckResult;
import upf.at.ban.store.ClientStore;

@Path("/clients")
@Produces(MediaType.APPLICATION_JSON)
public class ClientsResource {

    @POST
    @Path("/subscribe")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response subscribe(ClientProfile client) {

        // Gestió d'errors bàsica
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

        // 1) Verify Age (>=18) BEFORE saving
        // Normalize phone to E.164 (+34...)
        String phoneE164 = OpenGatewayAge.normalizeToE164(client.phone);
        if (phoneE164 == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"phone must be E.164-like (e.g., +346xxxxxxxx)\"}")
                    .build();
        }

        AgeCheckResult ageResult = OpenGatewayAge.verifyAgeThreshold(phoneE164, 18);

        if (!ageResult.ok) {
            // Error calling Open Gateway -> treat as service unavailable (fault management)
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"age verification service unavailable\",\"details\":\"" 
                            + OpenGatewayAge.jsonEscape(ageResult.details) +"\"}")
                    .build();
        }

        /*if (!ageResult.isAboveThreshold) {
            // Under 18: must return error and MUST NOT register the user
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"user is under required age\"}")
                    .build();
        }*/

        // 2) Save client
        // (Optional) overwrite phone in stored profile with normalized format:
        client.phone = phoneE164;

        ClientStore.save(client);

        return Response.ok(client).build();
    }

    @GET
    public Collection<ClientProfile> getClients() {
        return ClientStore.list();
    }
}
