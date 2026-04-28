package com.proteinviewer;

import com.proteinviewer.ui.MainWindow;
import javax.swing.*;

/**
 * ProteinViewer - A Java Swing application for 3D protein structure visualization.
 * Uses UniProt ID to fetch PDB structures and UniProt annotations.
 *
 * Features:
 *  - 3D rotatable/zoomable molecular structure renderer
 *  - Multiple display modes (backbone, ball-and-stick, space-fill, ribbon)
 *  - Protein sequence viewer with secondary structure annotation
 *  - Functional annotations from UniProt REST API
 *  - PDB metadata panel (resolution, method, chains, ligands)
 *  - Residue-level inspection on click
 *  - Export to PNG
 */
public class ProteinViewerApp {

    public static void main(String[] args) {
        // Enable system look and feel with Swing fallback
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Use default
            }
        }

        // Configure global rendering hints
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
