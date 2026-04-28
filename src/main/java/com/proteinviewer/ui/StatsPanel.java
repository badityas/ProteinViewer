package com.proteinviewer.ui;

import com.proteinviewer.model.*;
import com.proteinviewer.model.Residue.SecondaryStructure;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Statistics panel with pie charts, bar charts for composition and secondary structure.
 */
public class StatsPanel extends JPanel {

    private ProteinStructure structure;
    private final ChartPanel ssChart;
    private final ChartPanel compChart;
    private final JLabel statsLabel;

    public StatsPanel() {
        setBackground(new Color(18, 25, 42));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(sectionHeader("📊  Secondary Structure Composition"));
        ssChart = new ChartPanel();
        ssChart.setPreferredSize(new Dimension(260, 110));
        ssChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        ssChart.setAlignmentX(LEFT_ALIGNMENT);
        add(ssChart);

        add(Box.createVerticalStrut(8));
        add(sectionHeader("🧬  Amino Acid Composition (Top 10)"));
        compChart = new ChartPanel();
        compChart.setPreferredSize(new Dimension(260, 140));
        compChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        compChart.setAlignmentX(LEFT_ALIGNMENT);
        add(compChart);

        add(Box.createVerticalStrut(8));
        add(sectionHeader("📐  Structural Statistics"));
        statsLabel = new JLabel("<html></html>");
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsLabel.setForeground(new Color(200, 215, 245));
        statsLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(statsLabel);

        add(Box.createVerticalGlue());
    }

    public void updateStructure(ProteinStructure s) {
        this.structure = s;

        // ─ Secondary structure chart ─
        int helixCount = 0, sheetCount = 0, coilCount = 0, turnCount = 0;
        for (List<Residue> chain : s.chains.values()) {
            for (Residue r : chain) {
                switch (r.secStructure) {
                    case HELIX -> helixCount++;
                    case SHEET -> sheetCount++;
                    case TURN  -> turnCount++;
                    case COIL  -> coilCount++;
                }
            }
        }
        int total = helixCount + sheetCount + coilCount + turnCount;
        if (total > 0) {
            ssChart.setPieData(new String[]{"Helix","Sheet","Turn","Coil"},
                new double[]{helixCount, sheetCount, turnCount, coilCount},
                new Color[]{new Color(0xe74c3c), new Color(0xf1c40f),
                            new Color(0x27ae60), new Color(0x7f8c8d)});
        }

        // ─ Amino acid composition ─
        Map<String, Integer> aaCount = new TreeMap<>();
        for (List<Residue> chain : s.chains.values()) {
            for (Residue r : chain) {
                aaCount.merge(r.name, 1, Integer::sum);
            }
        }
        // Top 10
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(aaCount.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        int top = Math.min(10, sorted.size());
        String[] labels = new String[top];
        double[] values = new double[top];
        for (int i = 0; i < top; i++) {
            labels[i] = sorted.get(i).getKey();
            values[i] = sorted.get(i).getValue();
        }
        compChart.setBarData(labels, values);

        // ─ Stats text ─
        double dx = s.maxX - s.minX, dy = s.maxY - s.minY, dz = s.maxZ - s.minZ;
        String html = String.format(
            "<html><table cellpadding='1'>" +
            "<tr><td style='color:#8aabef'>Dimensions (Å):</td><td>%.1f × %.1f × %.1f</td></tr>" +
            "<tr><td style='color:#8aabef'>Helix residues:</td><td>%d (%.0f%%)</td></tr>" +
            "<tr><td style='color:#8aabef'>Sheet residues:</td><td>%d (%.0f%%)</td></tr>" +
            "<tr><td style='color:#8aabef'>Helix segments:</td><td>%d</td></tr>" +
            "<tr><td style='color:#8aabef'>Sheet strands:</td><td>%d</td></tr>" +
            "<tr><td style='color:#8aabef'>Ligands:</td><td>%s</td></tr>" +
            "<tr><td style='color:#8aabef'>HETATM atoms:</td><td>%d</td></tr>" +
            "</table></html>",
            dx, dy, dz,
            helixCount, total > 0 ? 100.0*helixCount/total : 0,
            sheetCount,  total > 0 ? 100.0*sheetCount/total : 0,
            s.helices.size(), s.sheets.size(),
            s.ligandNames.isEmpty() ? "None" : String.join(", ", s.ligandNames),
            s.hetAtoms.size()
        );
        statsLabel.setText(html);
        repaint();
    }

    private JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(new Color(140, 180, 255));
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 70, 110)));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return lbl;
    }

    // ─── Inner chart widget ───────────────────────────────────────────────────

    static class ChartPanel extends JPanel {
        enum Mode { PIE, BAR }
        private Mode mode = Mode.PIE;
        private String[] labels;
        private double[] values;
        private Color[] pieColors;

        private static final Color[] BAR_PALETTE = {
            new Color(0x3498db), new Color(0xe74c3c), new Color(0x2ecc71),
            new Color(0xf39c12), new Color(0x9b59b6), new Color(0x1abc9c),
            new Color(0xe67e22), new Color(0x34495e), new Color(0xff6b6b),
            new Color(0xa8e6cf)
        };

        ChartPanel() {
            setBackground(new Color(22, 32, 52));
        }

        void setPieData(String[] labels, double[] values, Color[] colors) {
            this.mode = Mode.PIE; this.labels = labels; this.values = values; this.pieColors = colors;
            repaint();
        }
        void setBarData(String[] labels, double[] values) {
            this.mode = Mode.BAR; this.labels = labels; this.values = values;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (labels == null || values == null) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (mode == Mode.PIE) drawPie(g);
            else                  drawBar(g);
        }

        private void drawPie(Graphics2D g) {
            int w = getWidth(), h = getHeight();
            double total = Arrays.stream(values).sum();
            if (total == 0) return;

            int r = Math.min(w/4, h/2) - 8;
            int cx = r + 12, cy = h/2;
            double start = -90;
            for (int i = 0; i < values.length; i++) {
                double sweep = (values[i] / total) * 360;
                g.setColor(pieColors[i]);
                g.fill(new Arc2D.Double(cx-r, cy-r, r*2, r*2, start, sweep, Arc2D.PIE));
                start += sweep;
            }
            // Border
            g.setColor(new Color(30, 42, 65));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(cx-r, cy-r, r*2, r*2));

            // Legend
            int lx = cx + r + 18, ly = 14;
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int i = 0; i < labels.length; i++) {
                g.setColor(pieColors[i]);
                g.fillRect(lx, ly - 9, 12, 12);
                g.setColor(new Color(200, 215, 245));
                int pct = (int)Math.round(100 * values[i] / total);
                g.drawString(labels[i] + " " + pct + "%", lx + 16, ly);
                ly += 18;
            }
        }

        private void drawBar(Graphics2D g) {
            if (labels == null || labels.length == 0) return;
            int w = getWidth(), h = getHeight();
            double maxVal = Arrays.stream(values).max().orElse(1);
            int barW = Math.max(5, (w - 30) / labels.length - 4);
            int maxH = h - 30;
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            for (int i = 0; i < labels.length; i++) {
                int bh = (int)((values[i] / maxVal) * maxH);
                int bx = 10 + i * (barW + 4);
                int by = h - 20 - bh;
                g.setColor(BAR_PALETTE[i % BAR_PALETTE.length]);
                g.fillRoundRect(bx, by, barW, bh, 3, 3);
                g.setColor(new Color(160, 180, 220));
                g.drawString(labels[i], bx, h - 6);
                g.setColor(new Color(200, 215, 245));
                g.drawString(String.valueOf((int)values[i]), bx, by - 2);
            }
        }
    }
}
