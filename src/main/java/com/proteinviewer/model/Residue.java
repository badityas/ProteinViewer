package com.proteinviewer.model;

import java.util.*;

/**
 * Represents one amino acid or nucleotide residue.
 */
public class Residue {
    public int seqNum;
    public String name;       // 3-letter code
    public char chainId;
    public List<Atom> atoms = new ArrayList<>();
    public SecondaryStructure secStructure = SecondaryStructure.COIL;

    // One-letter amino acid code
    public char getOneLetterCode() {
        return AminoAcidMap.toOneLetter(name);
    }

    public Atom getCA() {
        return atoms.stream().filter(a -> a.name.equals("CA")).findFirst().orElse(null);
    }

    public enum SecondaryStructure {
        HELIX("α-Helix"), SHEET("β-Sheet"), COIL("Coil/Loop"), TURN("Turn");
        public final String label;
        SecondaryStructure(String label) { this.label = label; }
    }

    @Override public String toString() {
        return name + seqNum + "(" + chainId + ")";
    }
}
