package com.proteinviewer.ui;

import com.proteinviewer.model.*;
import com.proteinviewer.renderer.ProteinRenderer;
import com.proteinviewer.renderer.ProteinRenderer.*;
import com.proteinviewer.service.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Main application window: assembles toolbar, viewport, info panel,
 * sequence viewer, and stats panel.
 */
public class MainWindow extends JFrame {

    // Top toolbar
    private final JTextField uniprotField;
    private final JButton loadButton;
    private final JProgressBar progressBar;

    // Viewport
    private final ViewportPanel viewport;

    // Info / stats
    private final InfoPanel infoPanel;
    private final StatsPanel statsPanel;

    // Bottom status
    private final JLabel statusBar;
    private final JLabel hoverLabel;

    // Controls
    private final JComboBox<String> displayModeBox;
    private final JComboBox<String> colorSchemeBox;
    private final JCheckBox hetCheckbox, hydroCheckbox;
    private final JSlider atomScaleSlider;

    // Sequence
    private final SequencePanel sequencePanel;

    // Data
    private ProteinStructure currentStructure;

    public MainWindow() {
        super("ProteinViewer 3D — UniProt → PDB Structure Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);

        applyDarkTheme();

        // Build UI components
        viewport = new ViewportPanel();
        infoPanel = new InfoPanel();
        statsPanel = new StatsPanel();
        sequencePanel = new SequencePanel();
        statusBar = styledLabel("Ready. Enter a UniProt ID (e.g. P68871) to begin.");
        hoverLabel = styledLabel("");
        uniprotField = new JTextField(12);
        styleTextField(uniprotField);
        loadButton = styledButton("⟳  Load Structure");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setMaximumSize(new Dimension(120, 20));
        displayModeBox = new JComboBox<>(new String[]{
            "Cartoon", "Backbone", "Ball & Stick", "Space Fill", "Ribbon", "Wireframe"});
        colorSchemeBox = new JComboBox<>(new String[]{
            "Secondary Structure", "Element (CPK)", "Chain", "B-Factor", "Hydrophobicity"});
        hetCheckbox   = new JCheckBox("Ligands", true);
        hydroCheckbox = new JCheckBox("Hydrogens", false);
        atomScaleSlider = new JSlider(50, 200, 100);
        atomScaleSlider.setToolTipText("Atom scale");
        atomScaleSlider.setBackground(new Color(25, 35, 55));

        styleComboBox(displayModeBox);
        styleComboBox(colorSchemeBox);
        styleCheckbox(hetCheckbox);
        styleCheckbox(hydroCheckbox);

        // Assemble layout
        setContentPane(buildContentPane());

        // Wire up interactions
        wireInteractions();
    }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private JPanel buildContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(15, 22, 38));

        root.add(buildToolbar(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBackground(new Color(20, 30, 50));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 60, 100)));

        // Logo
        JLabel logo = new JLabel("🧬 ProteinViewer");
        logo.setFont(new Font("SansSerif", Font.BOLD, 16));
        logo.setForeground(new Color(100, 180, 255));
        bar.add(logo);

        bar.add(new JSeparator(SwingConstants.VERTICAL) {{setPreferredSize(new Dimension(1, 24));}});

        JLabel idLabel = styledLabel("UniProt ID:");
        idLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bar.add(idLabel);
        uniprotField.setPreferredSize(new Dimension(110, 28));
        bar.add(uniprotField);
        bar.add(loadButton);
        bar.add(progressBar);

        bar.add(new JSeparator(SwingConstants.VERTICAL) {{setPreferredSize(new Dimension(1, 24));}});

        bar.add(darkLabel("Mode:"));
        bar.add(displayModeBox);
        bar.add(darkLabel("Color:"));
        bar.add(colorSchemeBox);

        bar.add(new JSeparator(SwingConstants.VERTICAL) {{setPreferredSize(new Dimension(1, 24));}});

        bar.add(hetCheckbox);
        bar.add(hydroCheckbox);

        bar.add(darkLabel("Atom size:"));
        atomScaleSlider.setPreferredSize(new Dimension(80, 22));
        bar.add(atomScaleSlider);

        bar.add(new JSeparator(SwingConstants.VERTICAL) {{setPreferredSize(new Dimension(1, 24));}});

        JButton resetBtn = styledButton("⟳ Reset View");
        resetBtn.addActionListener(e -> viewport.resetView());
        bar.add(resetBtn);

        JButton exportBtn = styledButton("💾 Export PNG");
        exportBtn.addActionListener(e -> viewport.exportPng());
        bar.add(exportBtn);

        // Example quick-load buttons
        bar.add(new JSeparator(SwingConstants.VERTICAL) {{setPreferredSize(new Dimension(1, 24));}});
        bar.add(darkLabel("Examples:"));
        for (String[] ex : new String[][]{{"P68871","Hemoglobin"},{"P00533","EGFR"},{"P02769","Albumin"},{"P69905","Myoglobin"}}) {
            JButton btn = miniButton(ex[1]);
            final String id = ex[0];
            btn.addActionListener(e -> { uniprotField.setText(id); loadStructure(); });
            bar.add(btn);
        }

        return bar;
    }

    private JSplitPane buildCenter() {
        // Left: viewport + sequence strip
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setBackground(new Color(15, 22, 38));
        leftPanel.add(viewport, BorderLayout.CENTER);

        // Sequence strip at bottom of viewport
        JPanel seqWrapper = new JPanel(new BorderLayout());
        seqWrapper.setBackground(new Color(20, 28, 45));
        seqWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 60, 100)));
        JScrollPane seqScroll = new JScrollPane(sequencePanel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        seqScroll.getViewport().setBackground(new Color(20, 28, 45));
        seqScroll.setBorder(null);
        seqScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, 90));
        seqWrapper.add(SequencePanel.createLegend(), BorderLayout.NORTH);
        seqWrapper.add(seqScroll, BorderLayout.CENTER);
        seqWrapper.add(hoverLabel, BorderLayout.SOUTH);
        leftPanel.add(seqWrapper, BorderLayout.SOUTH);

        // Right: tabbed info/stats
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(new Color(18, 25, 42));
        tabs.setForeground(new Color(180, 200, 255));
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane infoScroll = new JScrollPane(infoPanel);
        infoScroll.getViewport().setBackground(new Color(18, 25, 42));
        infoScroll.setBorder(null);
        tabs.addTab("Info", infoScroll);
        JScrollPane statsScroll = new JScrollPane(statsPanel);
        statsScroll.getViewport().setBackground(new Color(18, 25, 42));
        statsScroll.setBorder(null);
        tabs.addTab("Statistics", statsScroll);
        tabs.setPreferredSize(new Dimension(290, 500));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tabs);
        split.setDividerLocation(960);
        split.setDividerSize(4);
        split.setBackground(new Color(15, 22, 38));
        split.setBorder(null);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(new Color(12, 18, 32));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 60, 100)));
        bar.add(statusBar, BorderLayout.WEST);
        JLabel hint = styledLabel("Drag: rotate  ·  Shift+Drag: pan  ·  Scroll: zoom  ·  Click: inspect atom  ·  R: reset view");
        hint.setForeground(new Color(80, 100, 140));
        bar.add(hint, BorderLayout.EAST);
        bar.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return bar;
    }

    // ─── Wiring ───────────────────────────────────────────────────────────────

    private void wireInteractions() {
        // Load button
        loadButton.addActionListener(e -> loadStructure());
        uniprotField.addActionListener(e -> loadStructure());

        // Display mode
        displayModeBox.addActionListener(e -> {
            ProteinRenderer.DisplayMode[] modes = {ProteinRenderer.DisplayMode.CARTOON, ProteinRenderer.DisplayMode.BACKBONE,
                ProteinRenderer.DisplayMode.BALL_AND_STICK, ProteinRenderer.DisplayMode.SPACE_FILL,
                ProteinRenderer.DisplayMode.RIBBON, ProteinRenderer.DisplayMode.WIREFRAME};
            viewport.setDisplayMode(modes[displayModeBox.getSelectedIndex()]);
        });

        // Color scheme
        colorSchemeBox.addActionListener(e -> {
            ColorScheme[] schemes = {ColorScheme.SECONDARY, ColorScheme.ELEMENT,
                ColorScheme.CHAIN, ColorScheme.B_FACTOR, ColorScheme.HYDROPHOBICITY};
            viewport.setColorScheme(schemes[colorSchemeBox.getSelectedIndex()]);
        });

        hetCheckbox.addActionListener(e -> viewport.setShowHetAtoms(hetCheckbox.isSelected()));
        hydroCheckbox.addActionListener(e -> viewport.setShowHydrogens(hydroCheckbox.isSelected()));

        atomScaleSlider.addChangeListener(e ->
            viewport.setAtomScale(atomScaleSlider.getValue() / 100f));

        // Atom pick callback
        viewport.setOnAtomPicked(atom -> {
            if (atom != null && currentStructure != null) {
                Residue res = null;
                var chain = currentStructure.chains.get(atom.chainId);
                if (chain != null) for (Residue r : chain) if (r.seqNum == atom.resSeq) { res = r; break; }
                infoPanel.updateAtomInspector(atom, res);
                setStatus(String.format("Picked: %s %s%d (chain %c)  x=%.2f y=%.2f z=%.2f",
                    atom.name, atom.resName, atom.resSeq, atom.chainId, atom.x, atom.y, atom.z));
            }
        });

        // Hover label relay from sequence panel
        hoverLabel.setForeground(new Color(160, 190, 255));
        sequencePanel.setStatusLabel(hoverLabel);
    }

    // ─── Load workflow ────────────────────────────────────────────────────────

    private void loadStructure() {
        String id = uniprotField.getText().trim().toUpperCase();
        if (id.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter a UniProt accession ID.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setStatus("Fetching UniProt entry for " + id + "...");
        loadButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        SwingWorker<ProteinStructure, String> worker = new SwingWorker<>() {
            @Override protected ProteinStructure doInBackground() throws Exception {
                ProteinStructure s = new ProteinStructure();

                publish("Querying UniProt for " + id + "...");
                String pdbId = UniProtService.fetchAndPopulate(id, s);

                if (pdbId == null) {
                    throw new Exception("No PDB structure found for UniProt ID: " + id +
                        "\nTry a different accession or check that a 3D structure is available.");
                }

                s.pdbId = pdbId;
                publish("Downloading PDB structure " + pdbId + "...");
                PdbService.fetchAndParse(pdbId, s);

                publish("Rendering " + pdbId + "...");
                return s;
            }

            @Override protected void process(java.util.List<String> chunks) {
                setStatus(chunks.get(chunks.size()-1));
            }

            @Override protected void done() {
                try {
                    currentStructure = get();
                    viewport.setStructure(currentStructure);
                    sequencePanel.setStructure(currentStructure);
                    infoPanel.updateStructure(currentStructure);
                    statsPanel.updateStructure(currentStructure);
                    infoPanel.updateAtomInspector(null, null);

                    setTitle("ProteinViewer — " + currentStructure.proteinName +
                             " (" + currentStructure.pdbId + ")");
                    setStatus(String.format(
                        "Loaded %s | PDB: %s | %,d atoms | %,d residues | %d chain(s)",
                        currentStructure.proteinName, currentStructure.pdbId,
                        currentStructure.getAtomCount(), currentStructure.getTotalResidueCount(),
                        currentStructure.getChainCount()));

                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(MainWindow.this,
                        "Failed to load structure:\n" + msg, "Load Error", JOptionPane.ERROR_MESSAGE);
                    setStatus("Load failed: " + msg);
                } finally {
                    loadButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    // ─── Theme / styling helpers ──────────────────────────────────────────────

    private void applyDarkTheme() {
        UIManager.put("Panel.background",          new Color(18, 25, 42));
        UIManager.put("OptionPane.background",     new Color(25, 35, 60));
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("Button.background",         new Color(40, 60, 100));
        UIManager.put("Button.foreground",         Color.WHITE);
        UIManager.put("ComboBox.background",       new Color(30, 42, 70));
        UIManager.put("ComboBox.foreground",       new Color(200, 215, 255));
        UIManager.put("TextField.background",      new Color(25, 35, 58));
        UIManager.put("TextField.foreground",      Color.WHITE);
        UIManager.put("TextField.caretForeground", Color.WHITE);
        UIManager.put("ScrollBar.thumb",           new Color(50, 70, 120));
        UIManager.put("ScrollBar.track",           new Color(20, 30, 50));
        UIManager.put("TabbedPane.background",     new Color(18, 25, 42));
        UIManager.put("TabbedPane.foreground",     new Color(180, 200, 255));
        UIManager.put("SplitPane.background",      new Color(15, 22, 38));
        UIManager.put("SplitPaneDivider.background",new Color(30, 45, 75));
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusBar.setText(" " + msg));
    }

    private JLabel styledLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(new Color(180, 200, 240));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return lbl;
    }

    private JLabel darkLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(new Color(140, 165, 210));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return lbl;
    }

    private JButton styledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(new Color(45, 85, 155));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 10, 28));
        return btn;
    }

    private JButton miniButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setBackground(new Color(35, 55, 95));
        btn.setForeground(new Color(180, 210, 255));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(text.length() * 7 + 12, 22));
        return btn;
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(new Color(25, 35, 58));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setFont(new Font("Monospaced", Font.BOLD, 14));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 90, 150), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
    }

    private void styleComboBox(JComboBox<?> cb) {
        cb.setBackground(new Color(30, 42, 70));
        cb.setForeground(new Color(200, 215, 255));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setPreferredSize(new Dimension(cb.getPreferredSize().width + 10, 26));
    }

    private void styleCheckbox(JCheckBox cb) {
        cb.setBackground(new Color(20, 30, 50));
        cb.setForeground(new Color(180, 200, 240));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setFocusPainted(false);
    }
}
