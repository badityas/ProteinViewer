package com.proteinviewer.model;

/**
 * Represents a single atom from a PDB file.
 */
public class Atom {
    public int serial;
    public String name;       // e.g. "CA", "N", "C", "O"
    public String altLoc;
    public String resName;    // 3-letter residue name
    public char chainId;
    public int resSeq;
    public String iCode;
    public double x, y, z;
    public double occupancy;
    public double tempFactor; // B-factor
    public String element;    // e.g. "C", "N", "O", "S"

    // Calculated during load
    public double[] color = new double[]{0.5, 0.5, 0.5}; // RGB 0-1
    public boolean isBackbone;
    public boolean isHetAtom;

    public Atom() {}

    public Atom(int serial, String name, String resName, char chainId,
                int resSeq, double x, double y, double z, String element, boolean isHet) {
        this.serial = serial;
        this.name = name.trim();
        this.resName = resName.trim();
        this.chainId = chainId;
        this.resSeq = resSeq;
        this.x = x;
        this.y = y;
        this.z = z;
        this.element = element.trim();
        this.isHetAtom = isHet;
        this.isBackbone = isBackboneAtom(this.name);
        assignColor();
    }

    private boolean isBackboneAtom(String atomName) {
        return atomName.equals("CA") || atomName.equals("C") ||
               atomName.equals("N")  || atomName.equals("O");
    }

    /** CPK coloring scheme */
    public void assignColor() {
        String elem = (element != null && !element.isBlank()) ? element.toUpperCase() : deriveElement();
        switch (elem) {
            case "C"  -> color = new double[]{0.50, 0.50, 0.50};
            case "N"  -> color = new double[]{0.13, 0.47, 0.71};
            case "O"  -> color = new double[]{0.84, 0.18, 0.15};
            case "S"  -> color = new double[]{1.00, 0.82, 0.14};
            case "H"  -> color = new double[]{0.95, 0.95, 0.95};
            case "P"  -> color = new double[]{1.00, 0.50, 0.00};
            case "FE" -> color = new double[]{0.87, 0.40, 0.20};
            case "ZN" -> color = new double[]{0.49, 0.50, 0.69};
            case "CA" -> color = new double[]{0.24, 0.75, 0.24};
            case "MG" -> color = new double[]{0.54, 1.00, 0.00};
            default   -> color = new double[]{0.80, 0.80, 0.80};
        }
    }

    private String deriveElement() {
        if (name == null || name.isBlank()) return "C";
        // Strip digits and whitespace, take first letter
        String stripped = name.replaceAll("[0-9 ]", "");
        return stripped.isEmpty() ? "C" : stripped.substring(0, 1).toUpperCase();
    }

    /** Van der Waals radius in Angstroms */
    public double getVdwRadius() {
        String elem = (element != null && !element.isBlank()) ? element.toUpperCase() : deriveElement();
        return switch (elem) {
            case "H"  -> 1.20;
            case "C"  -> 1.70;
            case "N"  -> 1.55;
            case "O"  -> 1.52;
            case "S"  -> 1.80;
            case "P"  -> 1.80;
            case "FE" -> 1.40;
            case "ZN" -> 1.39;
            default   -> 1.50;
        };
    }

    @Override
    public String toString() {
        return String.format("Atom[%s %s %s%d chain=%c]", name, resName, resSeq, resSeq, chainId);
    }
}
