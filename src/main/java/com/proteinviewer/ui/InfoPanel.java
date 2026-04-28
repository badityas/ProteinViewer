package com.proteinviewer.ui;

import com.proteinviewer.model.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.Map;

/**
 * Side panel showing UniProt + PDB metadata for the loaded protein.
 */
public class InfoPanel extends JPanel {

    private final JTextArea functionArea;
    private final JLabel nameLabel, geneLabel, orgLabel, pdbLabel, methodLabel;
    private final JLabel resolutionLabel, chainsLabel, atomsLabel, residuesLabel;
    private final JLabel ligandLabel, locationLabel;
    private final JTextArea keywordsArea;
    private final JPanel activeSitesPanel;

    // Atom inspector
    private final JLabel atomNameLabel, atomResLabel, atomChainLabel;
    private final JLabel atomElemLabel, atomBfactLabel, atomCoordsLabel;

    public InfoPanel() {
        setBackground(new Color(18, 25, 42));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(280, 600));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ── Protein info ──────────────────────────────────────────────────────
        add(sectionHeader("📋  Protein Information"));

        nameLabel      = infoRow("Name",     "—");
        geneLabel      = infoRow("Gene",     "—");
        orgLabel       = infoRow("Organism", "—");
        locationLabel  = infoRow("Location", "—");

        add(Box.createVerticalStrut(4));
        add(sectionHeader("📁  Structure"));

        pdbLabel       = infoRow("PDB ID",     "—");
        methodLabel    = infoRow("Method",     "—");
        resolutionLabel= infoRow("Resolution", "—");
        chainsLabel    = infoRow("Chains",     "—");
        atomsLabel     = infoRow("Atoms",      "—");
        residuesLabel  = infoRow("Residues",   "—");
        ligandLabel    = infoRow("Ligands",    "—");

        add(Box.createVerticalStrut(4));
        add(sectionHeader("⚙  Function"));
        functionArea = styledTextArea();
        add(scrollWrap(functionArea, 90));

        add(Box.createVerticalStrut(4));
        add(sectionHeader("🏷  Keywords"));
        keywordsArea = styledTextArea();
        add(scrollWrap(keywordsArea, 55));

        add(Box.createVerticalStrut(4));
        add(sectionHeader("🔬  Active / Binding Sites"));
        activeSitesPanel = new JPanel();
        activeSitesPanel.setBackground(new Color(22, 32, 52));
        activeSitesPanel.setLayout(new BoxLayout(activeSitesPanel, BoxLayout.Y_AXIS));
        JScrollPane asp = new JScrollPane(activeSitesPanel);
        asp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        asp.setPreferredSize(new Dimension(260, 80));
        asp.setBorder(null);
        asp.getViewport().setBackground(new Color(22, 32, 52));
        add(asp);

        add(Box.createVerticalStrut(4));
        add(sectionHeader("🔍  Atom Inspector"));
        atomNameLabel  = infoRow("Atom",    "—");
        atomResLabel   = infoRow("Residue", "—");
        atomChainLabel = infoRow("Chain",   "—");
        atomElemLabel  = infoRow("Element", "—");
        atomBfactLabel = infoRow("B-Factor","—");
        atomCoordsLabel= infoRow("Coords",  "—");

        add(Box.createVerticalGlue());
    }

    public void updateStructure(ProteinStructure s) {
        nameLabel.setText(truncate(s.proteinName, 30));
        geneLabel.setText(s.geneName != null ? s.geneName : "—");
        orgLabel.setText(truncate(s.organism, 28));
        locationLabel.setText(truncate(s.subcellularLocation, 28));

        pdbLabel.setText(s.pdbId != null ? s.pdbId : "—");
        methodLabel.setText(s.experimentMethod != null ? s.experimentMethod : "—");
        resolutionLabel.setText(s.resolution > 0 ? String.format("%.2f Å", s.resolution) : "N/A");
        chainsLabel.setText(String.valueOf(s.getChainCount()));
        atomsLabel.setText(String.format("%,d", s.getAtomCount()));
        residuesLabel.setText(String.format("%,d", s.getTotalResidueCount()));
        ligandLabel.setText(s.ligandNames.isEmpty() ? "None" : String.join(", ", s.ligandNames));

        functionArea.setText(s.function != null ? s.function : "No function annotation available.");
        keywordsArea.setText(String.join("  ·  ", s.keywords));

        // Active sites
        activeSitesPanel.removeAll();
        for (ProteinStructure.FunctionalSite site : s.activeSites) {
            JLabel lbl = miniLabel("Active site " + site.position() + ": " + site.description(), new Color(255, 120, 80));
            activeSitesPanel.add(lbl);
        }
        for (ProteinStructure.FunctionalSite site : s.bindingSites) {
            JLabel lbl = miniLabel("Binding site " + site.position() + ": " + site.description(), new Color(80, 180, 255));
            activeSitesPanel.add(lbl);
        }
        if (s.activeSites.isEmpty() && s.bindingSites.isEmpty()) {
            activeSitesPanel.add(miniLabel("No annotated sites", Color.GRAY));
        }
        activeSitesPanel.revalidate();
    }

    public void updateAtomInspector(com.proteinviewer.model.Atom atom, Residue residue) {
        if (atom == null) {
            atomNameLabel.setText("—"); atomResLabel.setText("—"); atomChainLabel.setText("—");
            atomElemLabel.setText("—"); atomBfactLabel.setText("—"); atomCoordsLabel.setText("—");
            return;
        }
        atomNameLabel.setText(atom.name + (atom.isHetAtom ? " (HET)" : ""));
        atomResLabel.setText(residue != null
            ? residue.name + " " + residue.seqNum + " (" + AminoAcidMap.getFullName(residue.name) + ")"
            : atom.resName + " " + atom.resSeq);
        atomChainLabel.setText(String.valueOf(atom.chainId));
        atomElemLabel.setText(atom.element != null && !atom.element.isBlank() ? atom.element : "?");
        atomBfactLabel.setText(String.format("%.2f", atom.tempFactor));
        atomCoordsLabel.setText(String.format("%.2f, %.2f, %.2f", atom.x, atom.y, atom.z));
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(new Color(140, 180, 255));
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 70, 110)));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return lbl;
    }

    private JLabel infoRow(String key, String value) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(18, 25, 42));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel keyLbl = new JLabel(key + ":");
        keyLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        keyLbl.setForeground(new Color(130, 150, 190));
        keyLbl.setPreferredSize(new Dimension(80, 18));

        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        valLbl.setForeground(new Color(220, 230, 255));

        row.add(keyLbl, BorderLayout.WEST);
        row.add(valLbl, BorderLayout.CENTER);
        add(row);
        return valLbl;
    }

    private JTextArea styledTextArea() {
        JTextArea ta = new JTextArea();
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        ta.setEditable(false);
        ta.setBackground(new Color(22, 32, 52));
        ta.setForeground(new Color(200, 215, 245));
        ta.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return ta;
    }

    private JScrollPane scrollWrap(JTextArea ta, int h) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        sp.setPreferredSize(new Dimension(260, h));
        sp.setBorder(null);
        sp.setAlignmentX(LEFT_ALIGNMENT);
        return sp;
    }

    private JLabel miniLabel(String text, Color color) {
        JLabel lbl = new JLabel("• " + truncate(text, 34));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lbl.setForeground(color);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
