package com.proteinviewer.model;

import java.util.Map;

public class AminoAcidMap {
    private static final Map<String, Character> THREE_TO_ONE = Map.ofEntries(
        Map.entry("ALA",'A'), Map.entry("ARG",'R'), Map.entry("ASN",'N'),
        Map.entry("ASP",'D'), Map.entry("CYS",'C'), Map.entry("GLN",'Q'),
        Map.entry("GLU",'E'), Map.entry("GLY",'G'), Map.entry("HIS",'H'),
        Map.entry("ILE",'I'), Map.entry("LEU",'L'), Map.entry("LYS",'K'),
        Map.entry("MET",'M'), Map.entry("PHE",'F'), Map.entry("PRO",'P'),
        Map.entry("SER",'S'), Map.entry("THR",'T'), Map.entry("TRP",'W'),
        Map.entry("TYR",'Y'), Map.entry("VAL",'V'), Map.entry("SEC",'U'),
        Map.entry("PYL",'O'), Map.entry("ASX",'B'), Map.entry("GLX",'Z'),
        Map.entry("XLE",'J'), Map.entry("XAA",'X')
    );

    private static final Map<String, String> FULL_NAMES = Map.ofEntries(
        Map.entry("ALA","Alanine"),      Map.entry("ARG","Arginine"),
        Map.entry("ASN","Asparagine"),   Map.entry("ASP","Aspartate"),
        Map.entry("CYS","Cysteine"),     Map.entry("GLN","Glutamine"),
        Map.entry("GLU","Glutamate"),    Map.entry("GLY","Glycine"),
        Map.entry("HIS","Histidine"),    Map.entry("ILE","Isoleucine"),
        Map.entry("LEU","Leucine"),      Map.entry("LYS","Lysine"),
        Map.entry("MET","Methionine"),   Map.entry("PHE","Phenylalanine"),
        Map.entry("PRO","Proline"),      Map.entry("SER","Serine"),
        Map.entry("THR","Threonine"),    Map.entry("TRP","Tryptophan"),
        Map.entry("TYR","Tyrosine"),     Map.entry("VAL","Valine")
    );

    public static char toOneLetter(String threeLetterCode) {
        return THREE_TO_ONE.getOrDefault(threeLetterCode.toUpperCase(), 'X');
    }

    public static String getFullName(String threeLetterCode) {
        return FULL_NAMES.getOrDefault(threeLetterCode.toUpperCase(), threeLetterCode);
    }

    // Properties for color coding
    public static String getProperty(String threeLetterCode) {
        return switch (threeLetterCode.toUpperCase()) {
            case "ARG","LYS","HIS"         -> "Positively charged";
            case "ASP","GLU"               -> "Negatively charged";
            case "SER","THR","ASN","GLN"   -> "Polar uncharged";
            case "ALA","VAL","ILE","LEU",
                 "MET","PHE","TRP","PRO",
                 "TYR","GLY","CYS"         -> "Nonpolar";
            default                        -> "Unknown";
        };
    }
}
