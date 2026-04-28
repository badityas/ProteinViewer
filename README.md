# 🧬 ProteinViewer 3D

A **pure Java** desktop application for interactive 3D protein structure visualization,
built entirely with Java Swing. No external runtime dependencies — every line, from the
JSON parser to the 3D renderer, is written in Java.

---

## ✨ Features

### 3D Structure Viewer
| Feature | Details |
|---------|---------|
| **6 Display modes** | Cartoon, Backbone (Cα trace), Ball & Stick, Space Fill (VdW), Ribbon, Wireframe |
| **5 Color schemes** | Secondary Structure, Element (CPK), Chain rainbow, B-Factor heat, Hydrophobicity |
| **Interactive rotation** | Drag to rotate freely in 3D |
| **Pan & Zoom** | Shift+drag to pan · Scroll wheel to zoom |
| **Atom picking** | Click any atom to inspect it in the Info panel |
| **Keyboard shortcuts** | Arrow keys rotate · R resets view · +/- zoom |
| **PNG export** | Save current view at screen resolution |
| **Ligand display** | Toggle heteroatoms (ligands, cofactors) on/off |

### Protein Information Panel
- Full protein name, gene name, organism
- Subcellular location
- Experimental method (X-ray, NMR, Cryo-EM)
- Resolution, deposition date
- Chain count, atom count, residue count
- Ligand names (HETATM)
- Function annotation from UniProt
- Keywords (up to 15)
- Active sites and binding sites with residue numbers
- **Live atom inspector** — shows element, coordinates, B-factor, residue on click

### Sequence Viewer
- Scrollable one-letter sequence for all chains
- Color-coded by secondary structure (helix=red, sheet=yellow, coil=gray)
- Hover to show full residue name, property class, and secondary structure label
- Residue number ticks every 10 residues

### Statistics Panel
- **Pie chart** — secondary structure composition (% helix/sheet/coil)
- **Bar chart** — top 10 amino acid composition
- Bounding box dimensions in Ångströms
- Helix/sheet segment counts
- HETATM atom count

---

## 🚀 Quick Start

### Requirements
- **Java 17 or higher** (Java 21 recommended)
- Internet connection (fetches data from UniProt and RCSB PDB APIs)

### Build & Run

**Linux / macOS:**
```bash
chmod +x build.sh
./build.sh          # compile + run
./build.sh jar      # build ProteinViewer.jar
./build.sh compile  # compile only
```

**Windows:**
```cmd
build.bat           # compile + run
build.bat jar       # build ProteinViewer.jar
```

**Maven (IntelliJ / Eclipse):**
```bash
mvn compile exec:java -Dexec.mainClass=com.proteinviewer.ProteinViewerApp
# or
mvn package   # builds ProteinViewer-standalone.jar
java --enable-preview -jar target/ProteinViewer-standalone.jar
```

**Run pre-built JAR:**
```bash
java --enable-preview -jar ProteinViewer.jar
```

---

## 🧪 Example UniProt IDs

| UniProt ID | Protein | Notes |
|------------|---------|-------|
| `P68871` | Human Hemoglobin β | Classic textbook protein |
| `P69905` | Human Myoglobin | Oxygen storage, good for ribbon view |
| `P00533` | EGFR (Epidermal Growth Factor Receptor) | Cancer target, large kinase |
| `P02769` | Human Serum Albumin | Largest common plasma protein |
| `P00734` | Prothrombin | Coagulation cascade |
| `P01308` | Insulin | Disulfide-bonded hormone |
| `P00709` | α-Lactalbumin | Calcium-binding protein |
| `P00441` | Superoxide dismutase 1 (SOD1) | ALS-linked protein |

---

## 🗂 Project Structure

```
ProteinViewer/
├── src/main/java/
│   ├── com/proteinviewer/
│   │   ├── ProteinViewerApp.java        ← Entry point
│   │   ├── model/
│   │   │   ├── Atom.java               ← Single atom (coordinates, element, color)
│   │   │   ├── Residue.java            ← Amino acid residue + secondary structure
│   │   │   ├── ProteinStructure.java   ← Top-level model (chains, atoms, metadata)
│   │   │   └── AminoAcidMap.java       ← 3-letter ↔ 1-letter, properties
│   │   ├── service/
│   │   │   ├── UniProtService.java     ← REST calls to rest.uniprot.org
│   │   │   └── PdbService.java         ← Fetch & parse PDB files from RCSB
│   │   ├── renderer/
│   │   │   └── ProteinRenderer.java    ← Pure Java 3D software renderer
│   │   └── ui/
│   │       ├── MainWindow.java         ← Primary JFrame (toolbar, layout, wiring)
│   │       ├── ViewportPanel.java      ← 3D interactive viewport
│   │       ├── InfoPanel.java          ← Metadata & atom inspector sidebar
│   │       ├── SequencePanel.java      ← Scrollable annotated sequence strip
│   │       └── StatsPanel.java         ← Charts & structural statistics
│   └── org/json/
│       ├── JSONObject.java             ← Built-in JSON parser (no ext deps)
│       └── JSONArray.java
├── build.sh                            ← Linux/Mac build script
├── build.bat                           ← Windows build script
├── pom.xml                             ← Maven build (optional, for IDEs)
└── README.md
```

---

## 🔬 Architecture Notes

### 3D Renderer (Software Rendering — pure Java2D)
The renderer (`ProteinRenderer.java`) uses a classic pipeline:
1. **Model transform** — center structure at origin
2. **Rotation** — 3×3 matrix from Euler angles (Rx · Ry · Rz)
3. **Perspective projection** — simple focal-length divide
4. **Painter's algorithm** — sort by depth, draw back-to-front
5. **Shading** — depth-based brightness modulation; radial gradient for spheres

### Data Pipeline
```
User types UniProt ID
  → UniProtService: GET rest.uniprot.org/{id}.json
      - Protein name, gene, organism, function, keywords
      - Active/binding sites from "features" array
      - First cross-referenced PDB ID
  → PdbService: GET files.rcsb.org/download/{pdbId}.pdb
      - Parse ATOM/HETATM records → Atom objects
      - Parse HELIX/SHEET records → secondary structure
      - Cluster atoms into Residue → Chain → ProteinStructure
  → UI update on EDT (SwingWorker)
```

### Color Schemes
- **CPK / Element** — standard chemistry colors (C=gray, N=blue, O=red, S=yellow…)
- **Secondary structure** — helix=red, sheet=yellow, turn=green, coil=gray
- **Chain rainbow** — each chain gets a distinct color from a 10-color palette
- **B-factor** — blue (low) → red (high), normalized to 80 Å²
- **Hydrophobicity** — Kyte–Doolittle scale, blue (hydrophilic) → red (hydrophobic)

---

## ⚙️ Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `R` | Reset view (center + default rotation) |
| `+` / `=` | Zoom in |
| `-` | Zoom out |
| `←` `→` | Rotate around Y axis |
| `↑` `↓` | Rotate around X axis |
| Mouse drag | Rotate freely |
| Shift + drag | Pan |
| Scroll wheel | Zoom |
| Click atom | Inspect (shows in Info panel) |

---

## 📋 API Used (free, no key required)

| API | Endpoint |
|-----|----------|
| UniProt REST | `https://rest.uniprot.org/uniprotkb/{id}.json` |
| RCSB PDB | `https://files.rcsb.org/download/{pdbId}.pdb` |

---

## 📦 Importing as a Library

The project is structured so the core packages can be used independently:

```java
import com.proteinviewer.model.ProteinStructure;
import com.proteinviewer.service.UniProtService;
import com.proteinviewer.service.PdbService;
import com.proteinviewer.renderer.ProteinRenderer;

// Fetch and parse
ProteinStructure structure = new ProteinStructure();
String pdbId = UniProtService.fetchAndPopulate("P68871", structure);
PdbService.fetchAndParse(pdbId, structure);

// Render to a BufferedImage (embed in any Swing component)
ProteinRenderer renderer = new ProteinRenderer();
renderer.setDisplayMode(ProteinRenderer.DisplayMode.CARTOON);
renderer.setColorScheme(ProteinRenderer.ColorScheme.SECONDARY);
BufferedImage frame = renderer.render(structure, 800, 600);
```
