package com.proteinviewer.model;

import java.util.*;

/**
 * Top-level model holding all parsed structure data plus UniProt metadata.
 */
public class ProteinStructure {

    // PDB data
    public String pdbId;
    public String title;
    public String experimentMethod; // X-RAY, NMR, CRYO-EM, etc.
    public double resolution = -1;
    public String depositionDate;
    public String releaseDate;

    // UniProt data
    public String uniprotId;
    public String proteinName;
    public String geneName;
    public String organism;
    public String function;
    public String subcellularLocation;
    public int sequenceLength;
    public String sequence;
    public List<String> keywords = new ArrayList<>();
    public List<FunctionalSite> activeSites = new ArrayList<>();
    public List<FunctionalSite> bindingSites = new ArrayList<>();
    public List<DisulfideBond> disulfideBonds = new ArrayList<>();

    // Structural data
    public List<Atom> atoms = new ArrayList<>();
    public List<Atom> hetAtoms = new ArrayList<>();  // ligands, water, etc.
    public Map<Character, List<Residue>> chains = new LinkedHashMap<>();
    public List<HelixRecord> helices = new ArrayList<>();
    public List<SheetRecord> sheets = new ArrayList<>();
    public Set<String> ligandNames = new LinkedHashSet<>();

    // Bounding box (filled after parse)
    public double minX, maxX, minY, maxY, minZ, maxZ;
    public double centerX, centerY, centerZ;

    /** All non-water HETATM residues */
    public Set<String> getLigands() {
        return ligandNames;
    }

    public int getChainCount() { return chains.size(); }
    public int getAtomCount()  { return atoms.size(); }
    public int getTotalResidueCount() {
        return chains.values().stream().mapToInt(List::size).sum();
    }

    public void computeBoundingBox() {
        if (atoms.isEmpty()) return;
        minX = maxX = atoms.get(0).x;
        minY = maxY = atoms.get(0).y;
        minZ = maxZ = atoms.get(0).z;
        for (Atom a : atoms) {
            minX = Math.min(minX, a.x); maxX = Math.max(maxX, a.x);
            minY = Math.min(minY, a.y); maxY = Math.max(maxY, a.y);
            minZ = Math.min(minZ, a.z); maxZ = Math.max(maxZ, a.z);
        }
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
        centerZ = (minZ + maxZ) / 2;
    }

    public double getMaxExtent() {
        double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        return Math.max(dx, Math.max(dy, dz));
    }

    // ---- Inner records ----

    public record HelixRecord(int startSeq, int endSeq, char chainId, String type) {}
    public record SheetRecord(int startSeq, int endSeq, char chainId, int strandNum) {}
    public record FunctionalSite(int position, String description) {}
    public record DisulfideBond(int pos1, int pos2) {}
}
