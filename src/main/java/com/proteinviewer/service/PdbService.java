package com.proteinviewer.service;

import com.proteinviewer.model.*;
import com.proteinviewer.model.Residue.SecondaryStructure;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fetches PDB files from RCSB PDB and parses them into ProteinStructure.
 */
public class PdbService {

    private static final String RCSB_PDB_URL = "https://files.rcsb.org/download/%s.pdb";
    private static final String RCSB_CIF_FALLBACK = "https://files.rcsb.org/download/%s.cif";

    public static void fetchAndParse(String pdbId, ProteinStructure structure) throws IOException {
        String url = String.format(RCSB_PDB_URL, pdbId.toUpperCase());
        String pdbText;
        try {
            pdbText = fetchUrl(url);
        } catch (IOException e) {
            throw new IOException("Failed to fetch PDB file for " + pdbId + ": " + e.getMessage());
        }
        parsePdb(pdbText, structure);
    }

    // ─── Parser ──────────────────────────────────────────────────────────────

    private static void parsePdb(String pdbText, ProteinStructure structure) {
        Map<String, List<Atom>> chainAtomMap = new LinkedHashMap<>();
        // Temp: chain -> (resSeq -> Residue)
        Map<Character, TreeMap<Integer, Residue>> chainResidueMap = new LinkedHashMap<>();

        String[] lines = pdbText.split("\n");
        for (String line : lines) {
            if (line.length() < 6) continue;
            String recType = line.substring(0, 6).trim();

            switch (recType) {
                case "HEADER" -> parseHeader(line, structure);
                case "TITLE"  -> parseTitle(line, structure);
                case "EXPDTA" -> parseExpData(line, structure);
                case "REMARK" -> parseRemark(line, structure);
                case "HELIX"  -> parseHelix(line, structure);
                case "SHEET"  -> parseSheet(line, structure);
                case "ATOM"   -> {
                    Atom a = parseAtomLine(line, false);
                    if (a != null) {
                        structure.atoms.add(a);
                        chainAtomMap.computeIfAbsent(String.valueOf(a.chainId), k -> new ArrayList<>()).add(a);
                        chainResidueMap
                            .computeIfAbsent(a.chainId, k -> new TreeMap<>())
                            .computeIfAbsent(a.resSeq, k -> createResidue(a))
                            .atoms.add(a);
                    }
                }
                case "HETATM" -> {
                    Atom a = parseAtomLine(line, true);
                    if (a != null) {
                        structure.hetAtoms.add(a);
                        if (!a.resName.equals("HOH") && !a.resName.equals("WAT")) {
                            structure.ligandNames.add(a.resName);
                        }
                    }
                }
            }
        }

        // Build chains
        for (Map.Entry<Character, TreeMap<Integer, Residue>> entry : chainResidueMap.entrySet()) {
            structure.chains.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }

        // Apply secondary structure annotations
        applySecondaryStructure(structure);

        // Compute bounding box
        structure.computeBoundingBox();

        // Set pdbId if not already set
        if (structure.pdbId == null || structure.pdbId.isBlank()) {
            structure.pdbId = "UNKNOWN";
        }
    }

    private static void parseHeader(String line, ProteinStructure s) {
        if (line.length() >= 62) {
            s.pdbId = line.substring(62, Math.min(66, line.length())).trim();
        }
        if (line.length() >= 50) {
            s.depositionDate = line.substring(50, Math.min(59, line.length())).trim();
        }
    }

    private static void parseTitle(String line, ProteinStructure s) {
        String part = line.length() > 10 ? line.substring(10).trim() : "";
        if (s.title == null) s.title = part;
        else s.title = s.title + " " + part;
    }

    private static void parseExpData(String line, ProteinStructure s) {
        if (line.length() > 10) {
            s.experimentMethod = line.substring(10).trim()
                .replace("TECHNIQUE:", "").replace(";","").trim();
        }
    }

    private static void parseRemark(String line, ProteinStructure s) {
        if (line.length() > 11 && line.substring(7, 10).trim().equals("2")) {
            String content = line.substring(11).trim();
            if (content.startsWith("RESOLUTION.")) {
                try {
                    String resStr = content.replace("RESOLUTION.", "")
                        .replace("ANGSTROMS.", "").trim().split("\\s+")[0];
                    s.resolution = Double.parseDouble(resStr);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void parseHelix(String line, ProteinStructure s) {
        try {
            char chainId = line.charAt(19);
            int startSeq = Integer.parseInt(line.substring(21, 25).trim());
            int endSeq   = Integer.parseInt(line.substring(33, 37).trim());
            String type  = line.length() > 38 ? line.substring(38, 40).trim() : "1";
            s.helices.add(new ProteinStructure.HelixRecord(startSeq, endSeq, chainId, type));
        } catch (Exception ignored) {}
    }

    private static void parseSheet(String line, ProteinStructure s) {
        try {
            int strandNum = Integer.parseInt(line.substring(7, 10).trim());
            char chainId  = line.charAt(21);
            int startSeq  = Integer.parseInt(line.substring(22, 26).trim());
            int endSeq    = Integer.parseInt(line.substring(33, 37).trim());
            s.sheets.add(new ProteinStructure.SheetRecord(startSeq, endSeq, chainId, strandNum));
        } catch (Exception ignored) {}
    }

    private static Atom parseAtomLine(String line, boolean isHet) {
        try {
            int serial     = Integer.parseInt(line.substring(6, 11).trim());
            String name    = line.substring(12, 16).trim();
            String resName = line.substring(17, 20).trim();
            char chainId   = line.length() > 21 ? line.charAt(21) : 'A';
            int resSeq     = Integer.parseInt(line.substring(22, 26).trim());
            double x       = Double.parseDouble(line.substring(30, 38).trim());
            double y       = Double.parseDouble(line.substring(38, 46).trim());
            double z       = Double.parseDouble(line.substring(46, 54).trim());
            String element = line.length() >= 78 ? line.substring(76, 78).trim() : "";

            return new Atom(serial, name, resName, chainId, resSeq, x, y, z, element, isHet);
        } catch (Exception e) {
            return null;
        }
    }

    private static Residue createResidue(Atom a) {
        Residue r = new Residue();
        r.seqNum  = a.resSeq;
        r.name    = a.resName;
        r.chainId = a.chainId;
        return r;
    }

    private static void applySecondaryStructure(ProteinStructure structure) {
        // Build lookup: chain -> set of helix residue seqNums
        Map<Character, Set<Integer>> helixResidues = new HashMap<>();
        Map<Character, Set<Integer>> sheetResidues = new HashMap<>();

        for (ProteinStructure.HelixRecord h : structure.helices) {
            Set<Integer> set = helixResidues.computeIfAbsent(h.chainId(), k -> new HashSet<>());
            for (int i = h.startSeq(); i <= h.endSeq(); i++) set.add(i);
        }
        for (ProteinStructure.SheetRecord sh : structure.sheets) {
            Set<Integer> set = sheetResidues.computeIfAbsent(sh.chainId(), k -> new HashSet<>());
            for (int i = sh.startSeq(); i <= sh.endSeq(); i++) set.add(i);
        }

        for (Map.Entry<Character, List<Residue>> entry : structure.chains.entrySet()) {
            char ch = entry.getKey();
            Set<Integer> hSet = helixResidues.getOrDefault(ch, Collections.emptySet());
            Set<Integer> sSet = sheetResidues.getOrDefault(ch, Collections.emptySet());
            for (Residue r : entry.getValue()) {
                if (hSet.contains(r.seqNum))      r.secStructure = SecondaryStructure.HELIX;
                else if (sSet.contains(r.seqNum)) r.secStructure = SecondaryStructure.SHEET;
                else                              r.secStructure = SecondaryStructure.COIL;
            }
        }
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    private static String fetchUrl(String urlStr) throws IOException {
        URL url;
        try { url = new URI(urlStr).toURL(); }
        catch (URISyntaxException e) { throw new IOException("Bad URL: " + urlStr, e); }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "ProteinViewerApp/1.0 (Java)");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);

        int code = conn.getResponseCode();
        if (code == 404) throw new IOException("PDB entry not found (404)");
        if (code != 200) throw new IOException("HTTP error: " + code);

        try (InputStream is = conn.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder(1 << 20); // 1MB initial
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
