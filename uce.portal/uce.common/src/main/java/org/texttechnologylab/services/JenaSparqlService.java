package org.texttechnologylab.services;

import com.google.gson.Gson;
import org.jsoup.HttpStatusException;
import org.texttechnologylab.config.CommonConfig;
import org.texttechnologylab.models.dto.rdf.RDFAskDto;
import org.texttechnologylab.models.dto.rdf.RDFRequestDto;
import org.texttechnologylab.models.dto.rdf.RDFSelectQueryDto;
import org.texttechnologylab.models.util.HealthStatus;
import org.texttechnologylab.utils.SystemStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UPDATE 12-2024: I completely replaced the org.apache.jena.rdfconnection imports and libraries as they were
 * HORRIBLE. They threw so many shitty errors under circumstances that were un-debuggable. I had error occur
 * in docker environments *only*, that made absolutely no sense, so fk it, I parse them by hand now with regular requests.
 * I wasted enough time on this shit.
 */
public class JenaSparqlService {

    private final CommonConfig config = new CommonConfig();

    /**
     * Initializes the service like setting the default connection url. Service has to be initialized before it can be used.
     *
     * @return
     */
    public JenaSparqlService() {
        try{
            if (isServerResponsive()) {
                SystemStatus.JenaSparqlStatus = new HealthStatus(true, "Connection successful.", null);
            } else {
                SystemStatus.JenaSparqlStatus = new HealthStatus(false, "Server not reachable, ask failed.", null);
                System.out.println("Unable to connect to the Fuseki Sparql database, hello returned false.");
            }
        } catch (Exception ex){
            SystemStatus.JenaSparqlStatus = new HealthStatus(false, "Server returned an error, ask failed.", null);
        }
    }

    /**
     * Given a taxonid, it searches the sparql database for alternative names
     * E.g. BioFID id: https://www.biofid.de/bio-ontologies/gbif/4299368
     * Example call:
     * <p>
     * PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
     * PREFIX dwc: <http://rs.tdwg.org/dwc/terms/>
     * <p>
     * SELECT ?subject ?predicate ?object
     * WHERE {
     * VALUES ?subject {
     * <https://www.biofid.de/bio-ontologies/gbif/4299368>
     * <https://www.biofid.de/bio-ontologies/gbif/2345678>
     * <https://www.biofid.de/bio-ontologies/gbif/3456789>
     * }
     * ?subject ?predicate ?object .
     * <p>
     * FILTER(?predicate IN (<http://rs.tdwg.org/dwc/terms/vernacularName>, <http://rs.tdwg.org/dwc/terms/scientificName>))
     * }
     *
     * @return
     */
    public List<String> getAlternativeNamesOfTaxons(List<String> biofidIds) throws IOException {
        if(!SystemStatus.JenaSparqlStatus.isAlive()) {
            return new ArrayList<>();
        }

        biofidIds = biofidIds.stream().distinct().toList();
        // We want all objects where the subjects fit any of the given ids and the predicate is either vascularName or scientificName
        // By doing so, we get more possible alternative names
        var command = "SELECT ?subject ?predicate ?object " +
                "WHERE {" +
                "  VALUES ?subject { {BIOFID_IDS} }" +
                "  ?subject ?predicate ?object . " +
                "  FILTER(?predicate IN (<http://rs.tdwg.org/dwc/terms/vernacularName>, <http://rs.tdwg.org/dwc/terms/scientificName>)) " +
                "}";
        command = command.replace("{BIOFID_IDS}", String.join("\n", biofidIds.stream().map(id -> "<" + id + ">").toList()));
        var result = executeCommand(command, RDFSelectQueryDto.class);
        var alternativeNames = new ArrayList<String>();
        if(result == null || result.getResults() == null || result.getResults().getBindings() == null) return alternativeNames;

        for (var t : result.getResults().getBindings()) {
            alternativeNames.add(t.getObject().getValue());
        }
        return alternativeNames;
    }

    /**
     * Returns from e.g.: https://www.biofid.de/bio-ontologies/gbif/10428508 the taxon id that belongs to it.
     * We have that stored in our sparql database. Returns -1 if nothing was found.
     *
     * @return
     */
    public long biofidIdUrlToGbifTaxonId(String potentialBiofidId) throws IOException {
        if(!SystemStatus.JenaSparqlStatus.isAlive()) {
            return -1;
        }

        var command = "SELECT ?predicate ?object " +
                "WHERE {" +
                "  <{BIOFID_URL_ID}> <http://rs.tdwg.org/dwc/terms/taxonID> ?object ; " +
                "  . " +
                "}";
        command = command.replace("{BIOFID_URL_ID}", potentialBiofidId.trim());
        var result = executeCommand(command, RDFSelectQueryDto.class);
        if(result == null || result.getResults() == null || result.getResults().getBindings() == null) return -1;

        var gbifTaxonUrl = result.getResults().getBindings().getFirst().getObject().getValue();
        return Long.parseLong(Arrays.stream(gbifTaxonUrl.split("/")).toList().getLast());
    }

    /**
     * Executes a given command on the database and returns its List of QuerySolution
     *
     * @param command
     * @return
     */
    private <T extends RDFRequestDto> T executeCommand(String command, Class<T> clazz) throws IOException {
        var endPoint = config.getSparqlHost()
                + config.getSparqlEndpoint()
                + "?query="
                + URLEncoder.encode(command, StandardCharsets.UTF_8);
        var url = new URL(endPoint);
        var conn = (HttpURLConnection)url.openConnection();
        try{
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Parse the returned json
                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    var gson = new Gson();
                    return gson.fromJson(response.toString(), clazz);
                }
            } else {
                throw new HttpStatusException("Fuseki server returned error status: ", responseCode, endPoint);
            }
        } finally {
            conn.disconnect();
        }
    }

    private boolean isServerResponsive() throws IOException {
        String testQuery = "ASK WHERE { ?s ?p ?o }";
        var response = executeCommand(testQuery, RDFAskDto.class);
        if (response == null) return false;
        return response.isBool();
    }
}
