package upf.at.ban.api;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import upf.at.ban.model.Station;
import upf.at.ban.service.StationCacheService;

@Path("/stations")
@Produces(MediaType.APPLICATION_JSON)
public class StationsResource {

    // Add this annotation here to create a sub-path /list
    @Path("/list")
    @GET
    public List<Station> getStations() {
        return StationCacheService.getStations();
    }
}