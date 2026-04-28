package com.proteinviewer.ui;

import com.proteinviewer.model.*;
import com.proteinviewer.model.Residue.SecondaryStructure;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Scrollable sequence viewer showing residue one-letter codes,
 * colored by secondary structure, with residue number labels.
 */
public class SequencePanel extends JPanel {

    private ProteinStructure structure;
    private static final int CELL_W = 14, CELL_H = 22, LABEL_EVERY = 10;
    private static final Font SEQ_FONT  = new Font("Monospaced", Font.BOLD, 12);
    private static final Font NUM_FONT  = new Font("Monospaced", Font.PLAIN, 9);

    private int hoveredIndex = -1;
    private JLabel statusLabel;

    public SequencePanel() {
        setBackground(new Color(20, 28, 45));
        setPreferredSize(new Dimension(600, 80));
        initInteraction();
    }

    public void setStatusLabel(JLabel lbl) { this.statusLabel = lbl; }

    public void setStructure(ProteinStructure s) {
        this.structure = s;
        // Compute preferred width
        int totalRes = s != null ? s.getTotalResidueCount() : 0;
        setPreferredSize(new Dimension(Math.max(600, totalRes * CELL_W + 60), 80));
        revalidate(); repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (structure == null) return;
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x = 8;
        int globalIdx = 0;

        for (Map.Entry<Character, List<Residue>> entry : structure.chains.entrySet()) {
            char chainId = entry.getKey();
            List<Residue> residues = entry.getValue();

            // Chain label
            g.setColor(new Color(180, 200, 255));
            g.setFont(NUM_FONT);
            g.drawString("Chain " + chainId, x, 10);

            for (int i = 0; i < residues.size(); i++, globalIdx++) {
                Residue res = residues.get(i);
                char aa = res.getOneLetterCode();
                SecondaryStructure ss = res.secStructure;

                // Hovered highlight
                boolean hovered = (globalIdx == hoveredIndex);

                // Background for secondary structure
                Color bgColor = switch (ss) {
                    case HELIX -> new Color(180, 60, 60, 200);
                    case SHEET -> new Color(180, 160, 30, 200);
                    case TURN  -> new Color(50, 150, 80, 200);
                    case COIL  -> new Color(50, 60, 90, 200);
                };
                if (hovered) bgColor = bgColor.brighter();

                g.setColor(bgColor);
                g.fillRoundRect(x, 14, CELL_W-1, CELL_H, 3, 3);

                // Residue letter
                g.setFont(SEQ_FONT);
                g.setColor(hovered ? Color.WHITE : new Color(230, 235, 255));
                g.drawString(String.valueOf(aa), x + 2, 14 + CELL_H - 6);

                // Residue number label every LABEL_EVERY
                if (res.seqNum % LABEL_EVERY == 0 || i == 0) {
                    g.setFont(NUM_FONT);
                    g.setColor(new Color(150, 170, 210));
                    String numStr = String.valueOf(res.seqNum);
                    g.drawString(numStr, x, 14 + CELL_H + 10);
                }

                x += CELL_W;
            }
            // Gap between chains
            x += 20;
        }
    }

    private void initInteraction() {
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int idx = getResidueIndexAt(e.getX());
                if (idx != hoveredIndex) {
                    hoveredIndex = idx;
                    if (statusLabel != null && structure != null) {
                        updateStatusForIndex(idx);
                    }
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                hoveredIndex = -1;
                repaint();
            }
        });
    }

    private int getResidueIndexAt(int mouseX) {
        if (structure == null) return -1;
        int x = 8;
        int globalIdx = 0;
        for (Map.Entry<Character, List<Residue>> entry : structure.chains.entrySet()) {
            x += 0; // chain label not clickable
            for (Residue res : entry.getValue()) {
                if (mouseX >= x && mouseX < x + CELL_W) return globalIdx;
                x += CELL_W;
                globalIdx++;
            }
            x += 20;
        }
        return -1;
    }

    private void updateStatusForIndex(int idx) {
        int curr = 0;
        for (Map.Entry<Character, List<Residue>> entry : structure.chains.entrySet()) {
            for (Residue res : entry.getValue()) {
                if (curr == idx) {
                    String msg = String.format("Chain %c · %s%d (%s, %s) · %s",
                        res.chainId, res.name, res.seqNum,
                        AminoAcidMap.getFullName(res.name),
                        res.secStructure.label,
                        AminoAcidMap.getProperty(res.name));
                    statusLabel.setText(msg);
                    return;
                }
                curr++;
            }
        }
    }

    // Legend panel
    public static JPanel createLegend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        p.setBackground(new Color(20, 28, 45));
        Object[][] items = {
            {"α-Helix",  new Color(180, 60, 60)},
            {"β-Sheet",  new Color(180, 160, 30)},
            {"Turn",     new Color(50, 150, 80)},
            {"Coil",     new Color(50, 60, 90)},
        };
        for (Object[] item : items) {
            JLabel lbl = new JLabel("  " + item[0] + "  ");
            lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            lbl.setForeground(Color.WHITE);
            lbl.setBackground((Color)item[1]);
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            p.add(lbl);
        }
        return p;
    }
}
