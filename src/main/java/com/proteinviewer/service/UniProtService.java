package com.proteinviewer.service;

import com.proteinviewer.model.ProteinStructure;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Fetches protein metadata from UniProt REST API (https://rest.uniprot.org)
 * and maps PDB IDs.
 */
public class UniProtService {

    private static final String UNIPROT_BASE = "https://rest.uniprot.org/uniprotkb/";

    /**
     * Fetches UniProt entry and populates the structure.
     * @return the best PDB ID for this entry, or null if none found
     */
    public static String fetchAndPopulate(String uniprotId, ProteinStructure structure) throws IOException {
        String url = UNIPROT_BASE + uniprotId.toUpperCase().trim() + ".json";
        String json = fetchUrl(url);
        JSONObject root = new JSONObject(json);

        structure.uniprotId = uniprotId.toUpperCase().trim();

        // Protein name
        try {
            JSONObject proteinDesc = root.getJSONObject("proteinDescription");
            JSONObject recommended = proteinDesc.getJSONObject("recommendedName");
            structure.proteinName = recommended.getJSONObject("fullName").getString("value");
        } catch (Exception e) {
            structure.proteinName = uniprotId;
        }

        // Gene name
        try {
            JSONArray genes = root.getJSONArray("genes");
            if (!genes.isEmpty()) {
                structure.geneName = genes.getJSONObject(0)
                        .getJSONObject("geneName").getString("value");
            }
        } catch (Exception ignored) {}

        // Organism
        try {
            structure.organism = root.getJSONObject("organism")
                    .getJSONObject("scientificName").getString("value");
        } catch (Exception ignored) {}

        // Sequence
        try {
            JSONObject seq = root.getJSONObject("sequence");
            structure.sequence = seq.getString("value");
            structure.sequenceLength = seq.getInt("length");
        } catch (Exception ignored) {}

        // Comments: function, subcellular location
        try {
            JSONArray comments = root.getJSONArray("comments");
            StringBuilder funcBuilder = new StringBuilder();
            for (int i = 0; i < comments.length(); i++) {
                JSONObject comment = comments.getJSONObject(i);
                String type = comment.getString("commentType");
                if (type.equals("FUNCTION")) {
                    JSONArray texts = comment.getJSONArray("texts");
                    for (int j = 0; j < texts.length(); j++) {
                        funcBuilder.append(texts.getJSONObject(j).getString("value")).append(" ");
                    }
                } else if (type.equals("SUBCELLULAR LOCATION")) {
                    try {
                        JSONArray locs = comment.getJSONArray("subcellularLocations");
                        StringBuilder locBuilder = new StringBuilder();
                        for (int j = 0; j < locs.length(); j++) {
                            JSONObject loc = locs.getJSONObject(j);
                            locBuilder.append(loc.getJSONObject("location").getString("value"));
                            if (j < locs.length() - 1) locBuilder.append(", ");
                        }
                        structure.subcellularLocation = locBuilder.toString();
                    } catch (Exception ignored) {}
                }
            }
            if (funcBuilder.length() > 0) {
                structure.function = funcBuilder.toString().trim();
            }
        } catch (Exception ignored) {}

        // Keywords
        try {
            JSONArray kws = root.getJSONArray("keywords");
            for (int i = 0; i < Math.min(kws.length(), 15); i++) {
                structure.keywords.add(kws.getJSONObject(i).getString("name"));
            }
        } catch (Exception ignored) {}

        // Active sites and binding sites from features
        try {
            JSONArray features = root.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONObject feat = features.getJSONObject(i);
                String type = feat.getString("type");
                int pos = feat.getJSONObject("location").getJSONObject("start").getInt("value");
                String desc = feat.optString("description", type);
                if (type.equalsIgnoreCase("Active site")) {
                    structure.activeSites.add(new ProteinStructure.FunctionalSite(pos, desc));
                } else if (type.equalsIgnoreCase("Binding site")) {
                    structure.bindingSites.add(new ProteinStructure.FunctionalSite(pos, desc));
                } else if (type.equalsIgnoreCase("Disulfide bond")) {
                    int pos2 = feat.getJSONObject("location").getJSONObject("end").getInt("value");
                    structure.disulfideBonds.add(new ProteinStructure.DisulfideBond(pos, pos2));
                }
            }
        } catch (Exception ignored) {}

        // Cross-references: find best PDB entry
        try {
            JSONArray xrefs = root.getJSONArray("uniProtKBCrossReferences");
            for (int i = 0; i < xrefs.length(); i++) {
                JSONObject xref = xrefs.getJSONObject(i);
                if (xref.getString("database").equals("PDB")) {
                    String pdbId = xref.getString("id");
                    // Return first PDB (usually highest quality)
                    return pdbId;
                }
            }
        } catch (Exception ignored) {}

        return null; // No PDB mapping found
    }

    private static String fetchUrl(String urlStr) throws IOException {
        URL url;
        try {
            url = new URI(urlStr).toURL();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Invalid URL: " + urlStr, e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "ProteinViewerApp/1.0 (Java)");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code == 400 || code == 404) {
            throw new IOException("UniProt entry not found: HTTP " + code);
        }
        if (code != 200) {
            throw new IOException("HTTP error fetching UniProt: " + code);
        }

        try (InputStream is = conn.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
