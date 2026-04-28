package com.proteinviewer.ui;

import com.proteinviewer.model.*;
import com.proteinviewer.renderer.ProteinRenderer;
import com.proteinviewer.renderer.ProteinRenderer.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Interactive 3D viewport panel.
 * Handles mouse drag (rotate), scroll (zoom), Shift+drag (pan), click (pick).
 */
public class ViewportPanel extends JPanel {

    private ProteinStructure structure;
    private final ProteinRenderer renderer = new ProteinRenderer();
    private BufferedImage frameBuffer;

    // Mouse state
    private int lastMouseX, lastMouseY;
    private boolean dragging = false;

    // Callbacks
    private Consumer<Atom> onAtomPicked;
    private Runnable onStructureChanged;

    // Performance: render on a background thread
    private volatile boolean renderPending = false;

    public ViewportPanel() {
        setBackground(new Color(15, 20, 35));
        setPreferredSize(new Dimension(700, 550));
        setMinimumSize(new Dimension(400, 300));
        initMouseListeners();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void setStructure(ProteinStructure s) {
        this.structure = s;
        renderer.resetView();
        scheduleRender();
    }

    public void setDisplayMode(ProteinRenderer.DisplayMode mode) {
        renderer.setDisplayMode(mode);
        scheduleRender();
    }

    public void setColorScheme(ColorScheme scheme) {
        renderer.setColorScheme(scheme);
        scheduleRender();
    }

    public void setShowHetAtoms(boolean v) { renderer.setShowHetAtoms(v); scheduleRender(); }
    public void setShowHydrogens(boolean v) { renderer.setShowHydrogens(v); scheduleRender(); }
    public void setAtomScale(float s) { renderer.setAtomScale(s); scheduleRender(); }
    public void resetView() { renderer.resetView(); scheduleRender(); }
    public void setOnAtomPicked(Consumer<Atom> cb) { this.onAtomPicked = cb; }

    /** Export current view to PNG */
    public void exportPng() {
        if (frameBuffer == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
        fc.setSelectedFile(new File("protein_" + (structure != null ? structure.pdbId : "view") + ".png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().endsWith(".png")) file = new File(file.getPath() + ".png");
            try {
                ImageIO.write(frameBuffer, "png", file);
                JOptionPane.showMessageDialog(this, "Image saved: " + file.getName(), "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    private void scheduleRender() {
        if (renderPending) return;
        renderPending = true;
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
                return renderer.render(structure, w, h);
            }
            @Override protected void done() {
                try {
                    frameBuffer = get();
                    renderPending = false;
                    repaint();
                } catch (Exception e) {
                    renderPending = false;
                }
            }
        };
        worker.execute();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frameBuffer != null) {
            g.drawImage(frameBuffer, 0, 0, getWidth(), getHeight(), null);
        } else {
            g.setColor(new Color(15, 20, 35));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(100, 120, 160));
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.drawString("Initializing viewer...", 40, getHeight()/2);
        }
    }

    @Override public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        scheduleRender();
    }

    // ─── Mouse interaction ────────────────────────────────────────────────────

    private void initMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX(); lastMouseY = e.getY();
                dragging = true;
                requestFocusInWindow();
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                lastMouseX = e.getX(); lastMouseY = e.getY();
                if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
                    renderer.pan(dx, dy);
                } else {
                    renderer.rotate(dx * 0.5, dy * 0.5);
                }
                scheduleRender();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 || e.getButton() == MouseEvent.BUTTON1) {
                    if (structure != null) {
                        renderer.pick(structure, e.getX(), e.getY(), getWidth(), getHeight());
                        Atom picked = renderer.getPickedAtom();
                        if (onAtomPicked != null) onAtomPicked.accept(picked);
                        scheduleRender();
                    }
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(e -> {
            double factor = e.getWheelRotation() < 0 ? 1.12 : 0.88;
            renderer.zoom(factor);
            scheduleRender();
        });

        // Keyboard shortcuts
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_R -> resetView();
                    case KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS -> { renderer.zoom(1.15); scheduleRender(); }
                    case KeyEvent.VK_MINUS -> { renderer.zoom(0.87); scheduleRender(); }
                    case KeyEvent.VK_LEFT  -> { renderer.rotate(-5, 0); scheduleRender(); }
                    case KeyEvent.VK_RIGHT -> { renderer.rotate( 5, 0); scheduleRender(); }
                    case KeyEvent.VK_UP    -> { renderer.rotate(0, -5); scheduleRender(); }
                    case KeyEvent.VK_DOWN  -> { renderer.rotate(0,  5); scheduleRender(); }
                }
            }
        });

        // Tooltip with controls hint
        setToolTipText("<html>Drag: Rotate &nbsp;|&nbsp; Shift+Drag: Pan &nbsp;|&nbsp; Scroll: Zoom<br>" +
                       "Click: Pick atom &nbsp;|&nbsp; R: Reset view &nbsp;|&nbsp; Arrow keys: Rotate</html>");
    }
}
