package com.proteinviewer.renderer;

import com.proteinviewer.model.*;
import com.proteinviewer.model.Residue.SecondaryStructure;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Pure Java 2D/3D software renderer for protein structures.
 * Implements a simple painter's algorithm with perspective projection.
 *
 * Supported display modes:
 *   BACKBONE        - Cα trace with secondary structure coloring
 *   BALL_AND_STICK  - Balls for atoms + sticks for bonds
 *   SPACE_FILL      - VdW spheres
 *   RIBBON          - Smooth ribbon through Cα atoms
 *   CARTOON         - Secondary structure cartoons (helices as cylinders, sheets as arrows)
 *   WIREFRAME       - Bonds only, no spheres
 *
 * Supported color schemes:
 *   ELEMENT         - CPK coloring
 *   SECONDARY       - By secondary structure (helix=red, sheet=yellow, coil=white)
 *   CHAIN           - Rainbow per chain
 *   B_FACTOR        - Blue→Red temperature factor
 *   HYDROPHOBICITY  - By residue property
 */
public class ProteinRenderer {

    public enum DisplayMode { BACKBONE, BALL_AND_STICK, SPACE_FILL, RIBBON, CARTOON, WIREFRAME }
    public enum ColorScheme  { ELEMENT, SECONDARY, CHAIN, B_FACTOR, HYDROPHOBICITY }

    // View state
    private double rotX = 20, rotY = 30, rotZ = 0;
    private double zoom = 1.0;
    private double panX = 0, panY = 0;
    private DisplayMode displayMode = DisplayMode.CARTOON;
    private ColorScheme colorScheme = ColorScheme.SECONDARY;
    private boolean showHetAtoms = true;
    private boolean showHydrogens = false;
    private boolean showSurface = false;
    private float atomScale = 1.0f;

    // Picked atom
    private Atom pickedAtom = null;
    private Residue pickedResidue = null;

    // Chain colors (rainbow)
    private static final Color[] CHAIN_COLORS = {
        new Color(0xe74c3c), new Color(0x3498db), new Color(0x2ecc71),
        new Color(0xf39c12), new Color(0x9b59b6), new Color(0x1abc9c),
        new Color(0xe67e22), new Color(0x34495e), new Color(0xff6b6b), new Color(0xa8e6cf)
    };

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public void setDisplayMode(DisplayMode m) { this.displayMode = m; }
    public void setColorScheme(ColorScheme c) { this.colorScheme = c; }
    public void setShowHetAtoms(boolean v)    { this.showHetAtoms = v; }
    public void setShowHydrogens(boolean v)   { this.showHydrogens = v; }
    public void setAtomScale(float s)         { this.atomScale = s; }
    public void rotate(double dX, double dY)  { rotY += dX; rotX += dY; }
    public void zoom(double factor)           { zoom = Math.max(0.1, Math.min(20.0, zoom * factor)); }
    public void pan(double dx, double dy)     { panX += dx; panY += dy; }
    public void resetView()                   { rotX=20; rotY=30; rotZ=0; zoom=1.0; panX=0; panY=0; }
    public Atom getPickedAtom()               { return pickedAtom; }
    public Residue getPickedResidue()         { return pickedResidue; }

    // ─── Main render ──────────────────────────────────────────────────────────

    public BufferedImage render(ProteinStructure structure, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        setupGraphics(g2);

        // Background gradient
        GradientPaint bg = new GradientPaint(0, 0, new Color(15, 20, 35),
                                              0, height, new Color(25, 35, 60));
        g2.setPaint(bg);
        g2.fillRect(0, 0, width, height);

        if (structure == null || structure.atoms.isEmpty()) {
            drawPlaceholder(g2, width, height);
            g2.dispose();
            return img;
        }

        // Build rotation matrix
        double[][] rot = buildRotationMatrix(rotX, rotY, rotZ);
        double scale = computeScale(structure, width, height);
        double cx = width / 2.0 + panX;
        double cy = height / 2.0 + panY;

        // Project atoms
        List<ProjectedAtom> projected = new ArrayList<>();
        List<Character> chainList = new ArrayList<>(structure.chains.keySet());

        for (Atom atom : structure.atoms) {
            if (!showHydrogens && isHydrogen(atom)) continue;
            ProjectedAtom pa = project(atom, rot, scale, cx, cy, structure);
            pa.chainIndex = chainList.indexOf(atom.chainId);
            projected.add(pa);
        }
        if (showHetAtoms) {
            for (Atom atom : structure.hetAtoms) {
                if (atom.resName.equals("HOH")) continue;
                ProjectedAtom pa = project(atom, rot, scale, cx, cy, structure);
                pa.chainIndex = 0;
                projected.add(pa);
            }
        }

        // Sort by depth (painter's algorithm)
        projected.sort(Comparator.comparingDouble(pa -> pa.depth));

        // Render based on mode
        switch (displayMode) {
            case BACKBONE    -> drawBackbone(g2, structure, rot, scale, cx, cy, chainList);
            case BALL_AND_STICK -> drawBallAndStick(g2, projected, structure, rot, scale, cx, cy);
            case SPACE_FILL  -> drawSpaceFill(g2, projected, scale);
            case RIBBON, CARTOON -> drawRibbon(g2, structure, rot, scale, cx, cy, chainList);
            case WIREFRAME   -> drawWireframe(g2, projected, structure, rot, scale, cx, cy);
        }

        // Draw picked atom highlight
        if (pickedAtom != null) {
            ProjectedAtom ppa = project(pickedAtom, rot, scale, cx, cy, structure);
            g2.setColor(new Color(255, 255, 0, 180));
            g2.setStroke(new BasicStroke(3f));
            double r = 12;
            g2.draw(new Ellipse2D.Double(ppa.sx - r, ppa.sy - r, r*2, r*2));
        }

        // Draw axis indicator
        drawAxes(g2, rot, width, height);

        g2.dispose();
        return img;
    }

    // ─── Backbone mode ────────────────────────────────────────────────────────

    private void drawBackbone(Graphics2D g2, ProteinStructure s, double[][] rot,
                               double scale, double cx, double cy, List<Character> chainList) {
        int chainIdx = 0;
        for (Map.Entry<Character, List<Residue>> entry : s.chains.entrySet()) {
            List<Residue> residues = entry.getValue();
            Color chainColor = CHAIN_COLORS[chainIdx % CHAIN_COLORS.length];
            ProjectedAtom prev = null;
            for (Residue res : residues) {
                Atom ca = res.getCA();
                if (ca == null) continue;
                ProjectedAtom pca = project(ca, rot, scale, cx, cy, s);
                Color c = getResidueColor(res, chainIdx, s);
                if (prev != null) {
                    float depth01 = (float)((pca.depth + 200) / 400.0);
                    float brightness = 0.5f + 0.5f * depth01;
                    Color lineColor = scaleBrightness(c, brightness);
                    g2.setColor(lineColor);
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Line2D.Double(prev.sx, prev.sy, pca.sx, pca.sy));
                }
                // Draw Cα dot
                g2.setColor(c);
                double r = 4 * atomScale;
                g2.fill(new Ellipse2D.Double(pca.sx - r, pca.sy - r, r*2, r*2));
                prev = pca;
            }
            chainIdx++;
        }
    }

    // ─── Ball and Stick ───────────────────────────────────────────────────────

    private void drawBallAndStick(Graphics2D g2, List<ProjectedAtom> projected,
                                   ProteinStructure s, double[][] rot, double scale,
                                   double cx, double cy) {
        // Draw sticks first
        drawBonds(g2, s, rot, scale, cx, cy, 2.0f, 0.3);
        // Draw balls
        for (ProjectedAtom pa : projected) {
            double r = 5 * atomScale;
            Color c = getAtomDisplayColor(pa.atom, 0, s);
            float depth01 = (float)((pa.depth + 200) / 400.0);
            c = scaleBrightness(c, 0.6f + 0.4f * depth01);
            drawSphere(g2, pa.sx, pa.sy, r, c);
        }
    }

    // ─── Space-fill (CPK) ─────────────────────────────────────────────────────

    private void drawSpaceFill(Graphics2D g2, List<ProjectedAtom> projected, double scale) {
        for (ProjectedAtom pa : projected) {
            double vdw = pa.atom.getVdwRadius() * scale * 0.15 * atomScale;
            float depth01 = (float)((pa.depth + 200) / 400.0);
            Color c = scaleBrightness(getAtomElementColor(pa.atom), 0.6f + 0.4f * depth01);
            drawSphere(g2, pa.sx, pa.sy, vdw, c);
        }
    }

    // ─── Ribbon / Cartoon ─────────────────────────────────────────────────────

    private void drawRibbon(Graphics2D g2, ProteinStructure s, double[][] rot,
                             double scale, double cx, double cy, List<Character> chainList) {
        int chainIdx = 0;
        for (Map.Entry<Character, List<Residue>> entry : s.chains.entrySet()) {
            List<Residue> residues = entry.getValue();
            drawChainRibbon(g2, residues, chainIdx, rot, scale, cx, cy, s);
            chainIdx++;
        }
    }

    private void drawChainRibbon(Graphics2D g2, List<Residue> residues, int chainIdx,
                                  double[][] rot, double scale, double cx, double cy,
                                  ProteinStructure s) {
        if (residues.size() < 2) return;

        // Group consecutive residues by secondary structure
        List<List<Residue>> segments = new ArrayList<>();
        List<SecondaryStructure> segTypes = new ArrayList<>();
        List<Residue> current = new ArrayList<>();
        SecondaryStructure currentType = residues.get(0).secStructure;

        for (Residue r : residues) {
            if (r.secStructure == currentType) {
                current.add(r);
            } else {
                segments.add(current);
                segTypes.add(currentType);
                current = new ArrayList<>();
                current.add(r);
                currentType = r.secStructure;
            }
        }
        segments.add(current);
        segTypes.add(currentType);

        // Draw each segment
        for (int si = 0; si < segments.size(); si++) {
            List<Residue> seg = segments.get(si);
            SecondaryStructure type = segTypes.get(si);

            // Collect projected Cα points
            List<double[]> points = new ArrayList<>();
            for (Residue r : seg) {
                Atom ca = r.getCA();
                if (ca == null) continue;
                ProjectedAtom p = project(ca, rot, scale, cx, cy, s);
                points.add(new double[]{p.sx, p.sy, p.depth});
            }
            if (points.size() < 2) continue;

            Color segColor = getSecondaryStructureColor(type);
            float avgDepth = 0;
            for (double[] pt : points) avgDepth += pt[2];
            avgDepth /= points.size();
            float brightness = 0.55f + 0.45f * (float)((avgDepth + 200) / 400.0);
            segColor = scaleBrightness(segColor, brightness);

            if (type == SecondaryStructure.HELIX && displayMode == DisplayMode.CARTOON) {
                drawHelixCartoon(g2, points, segColor);
            } else if (type == SecondaryStructure.SHEET && displayMode == DisplayMode.CARTOON) {
                drawSheetCartoon(g2, points, segColor);
            } else {
                drawSmoothRibbon(g2, points, segColor, type == SecondaryStructure.HELIX ? 10f : 4f);
            }
        }
    }

    private void drawHelixCartoon(Graphics2D g2, List<double[]> points, Color color) {
        if (points.size() < 2) return;
        // Draw as a thick tube
        drawSmoothRibbon(g2, points, color, 12f);
        // Add highlight
        Color highlight = scaleBrightness(color, 1.4f);
        List<double[]> shiftedPoints = new ArrayList<>();
        for (double[] p : points) shiftedPoints.add(new double[]{p[0]-2, p[1]-2, p[2]});
        drawSmoothRibbon(g2, shiftedPoints, highlight, 4f);
    }

    private void drawSheetCartoon(Graphics2D g2, List<double[]> points, Color color) {
        if (points.size() < 2) return;
        // Wide ribbon for sheet
        drawSmoothRibbon(g2, points, color, 16f);
        // Draw arrowhead at end
        double[] last = points.get(points.size()-1);
        double[] prev = points.get(points.size()-2);
        drawArrowhead(g2, prev, last, color, 20);
    }

    private void drawArrowhead(Graphics2D g2, double[] from, double[] to, Color color, int size) {
        double dx = to[0] - from[0], dy = to[1] - from[1];
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len < 1) return;
        dx /= len; dy /= len;
        double nx = -dy, ny = dx; // normal

        int[] xp = {(int)(to[0] + nx*size), (int)(to[0] - nx*size), (int)(to[0] + dx*size*0.8)};
        int[] yp = {(int)(to[1] + ny*size), (int)(to[1] - ny*size), (int)(to[1] + dy*size*0.8)};
        g2.setColor(color);
        g2.fillPolygon(xp, yp, 3);
    }

    private void drawSmoothRibbon(Graphics2D g2, List<double[]> points, Color color, float width) {
        if (points.size() < 2) return;
        Path2D path = new Path2D.Double();
        path.moveTo(points.get(0)[0], points.get(0)[1]);
        for (int i = 1; i < points.size() - 1; i++) {
            double mx = (points.get(i)[0] + points.get(i+1)[0]) / 2;
            double my = (points.get(i)[1] + points.get(i+1)[1]) / 2;
            path.quadTo(points.get(i)[0], points.get(i)[1], mx, my);
        }
        double[] last = points.get(points.size()-1);
        path.lineTo(last[0], last[1]);

        g2.setColor(color);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
        // Inner highlight
        g2.setColor(scaleBrightness(color, 1.35f));
        g2.setStroke(new BasicStroke(Math.max(1f, width * 0.25f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    // ─── Wireframe ────────────────────────────────────────────────────────────

    private void drawWireframe(Graphics2D g2, List<ProjectedAtom> projected, ProteinStructure s,
                                double[][] rot, double scale, double cx, double cy) {
        drawBonds(g2, s, rot, scale, cx, cy, 1.5f, 0.5);
    }

    // ─── Bond drawing ─────────────────────────────────────────────────────────

    private void drawBonds(Graphics2D g2, ProteinStructure s, double[][] rot, double scale,
                            double cx, double cy, float strokeWidth, double maxDist) {
        List<Atom> atoms = s.atoms;
        // Draw bonds between adjacent atoms in same residue, plus backbone bonds
        // (simplified: bond if distance < threshold)
        double bondThreshold = 1.9; // Angstroms

        for (int i = 0; i < atoms.size(); i++) {
            Atom a1 = atoms.get(i);
            if (!showHydrogens && isHydrogen(a1)) continue;
            for (int j = i+1; j < atoms.size(); j++) {
                Atom a2 = atoms.get(j);
                if (a1.chainId != a2.chainId) break;
                if (Math.abs(a1.resSeq - a2.resSeq) > 1) continue;
                if (!showHydrogens && isHydrogen(a2)) continue;
                double dist = distance3D(a1, a2);
                if (dist < bondThreshold) {
                    ProjectedAtom p1 = project(a1, rot, scale, cx, cy, s);
                    ProjectedAtom p2 = project(a2, rot, scale, cx, cy, s);
                    double depth = (p1.depth + p2.depth) / 2;
                    float brightness = 0.5f + 0.5f * (float)((depth + 200) / 400.0);
                    Color c1 = scaleBrightness(getAtomDisplayColor(a1, 0, s), brightness);
                    g2.setColor(c1);
                    g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Line2D.Double(p1.sx, p1.sy, p2.sx, p2.sy));
                }
            }
        }
    }

    // ─── Pick testing ─────────────────────────────────────────────────────────

    public void pick(ProteinStructure structure, int mouseX, int mouseY, int width, int height) {
        if (structure == null) return;
        double[][] rot = buildRotationMatrix(rotX, rotY, rotZ);
        double scale = computeScale(structure, width, height);
        double cx = width / 2.0 + panX, cy = height / 2.0 + panY;

        pickedAtom = null; pickedResidue = null;
        double minDist = 15;
        for (Atom atom : structure.atoms) {
            ProjectedAtom pa = project(atom, rot, scale, cx, cy, structure);
            double d = Math.hypot(pa.sx - mouseX, pa.sy - mouseY);
            if (d < minDist) {
                minDist = d;
                pickedAtom = atom;
            }
        }
        if (pickedAtom != null) {
            // Find residue
            List<Residue> residues = structure.chains.getOrDefault(pickedAtom.chainId, List.of());
            for (Residue r : residues) {
                if (r.seqNum == pickedAtom.resSeq) { pickedResidue = r; break; }
            }
        }
    }

    // ─── Projection ───────────────────────────────────────────────────────────

    private ProjectedAtom project(Atom atom, double[][] rot, double scale,
                                   double cx, double cy, ProteinStructure s) {
        // Center on structure
        double ax = atom.x - s.centerX;
        double ay = atom.y - s.centerY;
        double az = atom.z - s.centerZ;

        // Rotate
        double rx = rot[0][0]*ax + rot[0][1]*ay + rot[0][2]*az;
        double ry = rot[1][0]*ax + rot[1][1]*ay + rot[1][2]*az;
        double rz = rot[2][0]*ax + rot[2][1]*ay + rot[2][2]*az;

        // Simple perspective
        double fov = 300;
        double pz = fov + rz * scale * 0.05;
        double sx = cx + (rx * scale * fov) / pz;
        double sy = cy - (ry * scale * fov) / pz;

        ProjectedAtom pa = new ProjectedAtom();
        pa.atom  = atom;
        pa.sx    = sx;
        pa.sy    = sy;
        pa.depth = rz;
        return pa;
    }

    private double computeScale(ProteinStructure s, int width, int height) {
        double extent = s.getMaxExtent();
        if (extent <= 0) extent = 50;
        double base = Math.min(width, height) / (extent * 1.8);
        return base * zoom;
    }

    // ─── Color helpers ────────────────────────────────────────────────────────

    private Color getAtomDisplayColor(Atom atom, int chainIdx, ProteinStructure s) {
        return switch (colorScheme) {
            case ELEMENT  -> getAtomElementColor(atom);
            case CHAIN    -> CHAIN_COLORS[Math.abs(atom.chainId) % CHAIN_COLORS.length];
            case B_FACTOR -> bFactorColor(atom.tempFactor);
            case SECONDARY, HYDROPHOBICITY -> {
                // Find residue
                List<Residue> res = s.chains.getOrDefault(atom.chainId, List.of());
                for (Residue r : res) {
                    if (r.seqNum == atom.resSeq) {
                        yield colorScheme == ColorScheme.SECONDARY
                            ? getSecondaryStructureColor(r.secStructure)
                            : getHydrophobicityColor(r.name);
                    }
                }
                yield Color.GRAY;
            }
        };
    }

    private Color getResidueColor(Residue res, int chainIdx, ProteinStructure s) {
        return switch (colorScheme) {
            case ELEMENT  -> Color.GRAY;
            case CHAIN    -> CHAIN_COLORS[chainIdx % CHAIN_COLORS.length];
            case B_FACTOR -> { Atom ca = res.getCA(); yield ca != null ? bFactorColor(ca.tempFactor) : Color.GRAY; }
            case SECONDARY -> getSecondaryStructureColor(res.secStructure);
            case HYDROPHOBICITY -> getHydrophobicityColor(res.name);
        };
    }

    private Color getAtomElementColor(Atom atom) {
        double[] c = atom.color;
        return new Color((float)c[0], (float)c[1], (float)c[2]);
    }

    private Color getSecondaryStructureColor(SecondaryStructure ss) {
        return switch (ss) {
            case HELIX -> new Color(0xe74c3c);  // Red
            case SHEET -> new Color(0xf1c40f);  // Yellow
            case TURN  -> new Color(0x27ae60);  // Green
            case COIL  -> new Color(0xbdc3c7);  // Light gray
        };
    }

    private Color getHydrophobicityColor(String resName) {
        // Kyte-Doolittle hydrophobicity (simplified)
        double h = switch (resName.toUpperCase()) {
            case "ILE" -> 4.5; case "VAL" -> 4.2; case "LEU" -> 3.8;
            case "PHE" -> 2.8; case "CYS" -> 2.5; case "MET" -> 1.9;
            case "ALA" -> 1.8; case "GLY" -> -0.4; case "THR" -> -0.7;
            case "TRP" -> -0.9; case "SER" -> -0.8; case "TYR" -> -1.3;
            case "PRO" -> -1.6; case "HIS" -> -3.2; case "GLU" -> -3.5;
            case "GLN" -> -3.5; case "ASP" -> -3.5; case "ASN" -> -3.5;
            case "LYS" -> -3.9; case "ARG" -> -4.5;
            default -> 0.0;
        };
        // Map -4.5..4.5 to blue..white..red
        float t = (float)((h + 4.5) / 9.0);
        if (t < 0.5f) return new Color(2*t, 2*t, 1.0f);
        else          return new Color(1.0f, 2*(1-t), 2*(1-t));
    }

    private Color bFactorColor(double bf) {
        double norm = Math.min(1.0, Math.max(0.0, bf / 80.0));
        return new Color((float)norm, 0f, (float)(1.0 - norm));
    }

    private Color scaleBrightness(Color c, float factor) {
        float r = Math.min(1f, c.getRed()/255f * factor);
        float g = Math.min(1f, c.getGreen()/255f * factor);
        float b = Math.min(1f, c.getBlue()/255f * factor);
        return new Color(r, g, b);
    }

    // ─── Sphere drawing ───────────────────────────────────────────────────────

    private void drawSphere(Graphics2D g2, double cx, double cy, double r, Color color) {
        // Fill
        RadialGradientPaint grad = new RadialGradientPaint(
            (float)(cx - r*0.3), (float)(cy - r*0.3), (float)(r * 1.2),
            new float[]{0f, 1f},
            new Color[]{scaleBrightness(color, 1.6f), scaleBrightness(color, 0.5f)}
        );
        g2.setPaint(grad);
        g2.fill(new Ellipse2D.Double(cx-r, cy-r, r*2, r*2));
        // Specular highlight
        g2.setPaint(new Color(255,255,255,80));
        double hr = r * 0.35;
        g2.fill(new Ellipse2D.Double(cx-r*0.4-hr/2, cy-r*0.4-hr/2, hr, hr));
    }

    // ─── Axis indicator ───────────────────────────────────────────────────────

    private void drawAxes(Graphics2D g2, double[][] rot, int width, int height) {
        int ox = 50, oy = height - 50;
        int len = 30;
        String[] labels = {"X","Y","Z"};
        Color[] colors  = {new Color(0xff4444), new Color(0x44ff44), new Color(0x4488ff)};
        double[][] dirs = {{1,0,0},{0,1,0},{0,0,1}};
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        for (int i = 0; i < 3; i++) {
            double dx = rot[0][0]*dirs[i][0] + rot[0][1]*dirs[i][1] + rot[0][2]*dirs[i][2];
            double dy = rot[1][0]*dirs[i][0] + rot[1][1]*dirs[i][1] + rot[1][2]*dirs[i][2];
            int ex = (int)(ox + dx*len), ey = (int)(oy - dy*len);
            g2.setColor(colors[i]);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(ox, oy, ex, ey);
            g2.fillOval(ex-3, ey-3, 6, 6);
            g2.drawString(labels[i], ex+3, ey+4);
        }
    }

    // ─── Placeholder ──────────────────────────────────────────────────────────

    private void drawPlaceholder(Graphics2D g2, int width, int height) {
        g2.setColor(new Color(100, 120, 160));
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        String msg = "Enter a UniProt ID and click Load";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (width - fm.stringWidth(msg))/2, height/2);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String sub = "e.g.  P68871  (Hemoglobin)  ·  P00533  (EGFR)  ·  P02769  (Serum albumin)";
        fm = g2.getFontMetrics();
        g2.drawString(sub, (width - fm.stringWidth(sub))/2, height/2 + 35);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private void setupGraphics(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private double[][] buildRotationMatrix(double rx, double ry, double rz) {
        double sinX = Math.sin(Math.toRadians(rx)), cosX = Math.cos(Math.toRadians(rx));
        double sinY = Math.sin(Math.toRadians(ry)), cosY = Math.cos(Math.toRadians(ry));
        double sinZ = Math.sin(Math.toRadians(rz)), cosZ = Math.cos(Math.toRadians(rz));

        double[][] Rx = {{ 1, 0,     0    }, { 0,  cosX, -sinX}, { 0, sinX, cosX}};
        double[][] Ry = {{ cosY, 0, sinY  }, { 0,  1,    0    }, {-sinY, 0, cosY}};
        double[][] Rz = {{ cosZ, -sinZ, 0 }, { sinZ, cosZ, 0  }, { 0,    0,   1 }};

        return matMul(matMul(Rz, Ry), Rx);
    }

    private double[][] matMul(double[][] A, double[][] B) {
        double[][] C = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                for (int k = 0; k < 3; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    private double distance3D(Atom a, Atom b) {
        double dx = a.x-b.x, dy = a.y-b.y, dz = a.z-b.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private boolean isHydrogen(Atom a) {
        String elem = a.element != null ? a.element.trim().toUpperCase() : "";
        if (elem.equals("H") || elem.equals("D")) return true;
        String name = a.name.trim();
        return name.startsWith("H") || name.startsWith("D");
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    static class ProjectedAtom {
        Atom atom;
        double sx, sy, depth;
        int chainIndex;
    }
}
