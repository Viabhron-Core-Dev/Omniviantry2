# Macro-Engine Host & Plugin Architecture Plan
> **STATUS: NOT CHOSEN / SUPERSEDED**
> This draft plan has been superseded by the unified `/MASTER_WORKSPACE_TAB_PLAN.md`. Keep for historical reference only.

---


Omnivian is structured as an **Operating System-like Modular Engine Platform**. Instead of hardcoding specialized screens into a monolithic application, Omnivian separates the system into a **Pre-Compiled Native Host Skeleton** (shipped inside the APK) and **Pluggable Macro-Engine Flesh & Organs** (extensible, importable, and configurable).

### Foundational Tenets:
1. **The Core Backbone is the Storage & File Explorer**: Every workspace has an isolated storage directory (`files/workspaces/<workspace_id>`). All files (code, documents, media, zips, PDFs) are the single source of truth on disk.
2. **Engines as Macro-Plugins**: An engine is a full domain application—it provides:
   - A dedicated work view inside the workspace tab shell.
   - A scoped headless toolset that the AI can execute.
   - Custom rules, skills, and configuration.
   - Its own management entry under **Global Settings > General > Engines**.
3. **The 3-Phase Engine Progression (View First ➔ AI Interactive ➔ Editor Last)**:
   - **Phase 1 (View First / NOW)**: High-speed, lightweight visual rendering and inspection (see code, view PDF pages, browse Zip archives, render Markdown/PPT slides, preview images, browse web).
   - **Phase 2 (AI Interactive)**: Spatial, temporal, and semantic pointing (draw bounding boxes on images, highlight text/rows, pin video timestamps) with AI context dispatch.
   - **Phase 3 (Editor Last)**: Deep bi-directional interactive editing, mutation, and undo/redo stacks.
4. **Strict AI Boundary & Tab Permission Rule**:
   - The AI operates headlessly in the background.
   - The AI is **strictly scoped** to the tools of the current active engine.
   - The AI is **strictly forbidden** from opening new tabs or calling tools outside the active engine without presenting an explicit confirmation prompt to the user (`request_engine_access` / `request_open_tab`).
5. **Google Play Policy Compliance (No Dynamic Code Loading)**:
   - To strictly comply with Google Play's ban on dynamic executable code (`.dex` / `.so`), native host skeletons are pre-compiled into the APK.
   - User-created or imported dynamic engines run as sandboxed HTML5/CSS/JavaScript or WebAssembly bundles communicating with Android via a typed `@JavascriptInterface` bridge.
6. **Meta-Development / Ingestion Pipeline (Import ➔ Scan ➔ Adapt ➔ Deploy)**:
   - Users can import open-source standalone tools into the Workspace/Chat.
   - Omnivian's AI audits the tool, wraps it with the Omnivian Bridge API, sanitizes network/storage calls, and registers it as an active engine in Global Settings.

---

## 2. The 5 Pre-Compiled Native Host Skeletons

To support any file format without APK bloat or Play Store violations, Omnivian provides 5 pre-compiled host skeletons in the APK:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OMNIVIAN HOST SHELL                             │
│       (File Explorer + Tab Manager + Permission Engine Guard)          │
├────────────────────────────────────────────────────────────────────────┤
│                       5 NATIVE HOST SKELETONS                          │
│                                                                        │
│ 1. CODE & TEXT SKELETON                                                │
│    • Formats: .kt, .py, .js, .ts, .json, .sh, .txt, .sql               │
│    • Pipeline: Fast text buffer, line numbers, cursor, diff split      │
│                                                                        │
│ 2. DOCUMENT & PAGED SKELETON                                           │
│    • Formats: .md (Markdown), .pdf (Native PdfRenderer),               │
│               .slides.md (Pager), .zip (Tree Inspector)                │
│                                                                        │
│ 3. MEDIA & GRAPHICS SKELETON                                           │
│    • Formats: .png, .jpg, .svg, .webp, .mp4, .mp3, .wav                │
│    • Pipeline: Pan/Zoom Canvas, Media3/ExoPlayer, Waveform Canvas      │
│                                                                        │
│ 4. SPATIAL & 3D SKELETON                                               │
│    • Formats: .gltf, .glb, .geojson, vector tiles                      │
│    • Pipeline: Filament / <model-viewer> and Leaflet/MapLibre          │
│                                                                        │
│ 5. SANDBOX WEBVIEW HOST (Universal Plugin Runner)                      │
│    • Formats: User-imported HTML5/Wasm macro-engines                   │
│    • Pipeline: Zero-trust local sandbox + @JavascriptInterface IPC     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Initial "View First" Engine Matrix

| Engine | Host Skeleton | View First (Phase 1) | AI Interactive (Phase 2) | Full Editor (Phase 3) |
|---|---|---|---|---|
| **Code** | Text Skeleton | Syntax highlighted viewer + Line numbers + Diff Split | AI code explanations, inline diff suggestions | Real-time code editor with auto-complete |
| **PDF** | Document Skeleton | Native Android `PdfRenderer` page scroll | Page selection ➔ AI extracts text / analyzes diagrams | Annotations, highlights, form fill |
| **Zip Archive** | Document Skeleton | Virtual folder tree inspector without extracting | AI inspects archive contents ➔ selective extraction | Archive creation, file injection |
| **Presentation** | Document Skeleton | Swipeable slide cards (`HorizontalPager` via Markdown) | AI generates slide decks, edits slide notes | WYSIWYG slide layout designer |
| **Document / Markdown** | Document Skeleton | Rich typography Markdown rendering | Highlight text ➔ AI rewrite/summarize | Bi-directional rich-text editor |
| **Table / Data Grid** | Document Skeleton | CSV/TSV table grid with sorting | Tap row/column ➔ AI formula / data transformation | Cell editor, CSV export |
| **Image & Canvas** | Media Skeleton | Pan/zoom visual viewport | **Spatial Bounding Box**: Drag box ➔ AI inspects region | Pencil, arrows, cropping, filters |
| **Browser** | Sandbox WebView | Lightweight web navigation & local preview | **DOM / Screenshot Capture**: One-tap AI context dump | Full developer tools & JS console |

---

## 4. Universal Macro-Engine Plugin Contract

Every engine (native or dynamic) implements a uniform lifecycle contract:

```kotlin
interface OmnivianEnginePlugin {
    val id: String                        // e.g. "com.omnivian.engine.pdf"
    val displayName: String               // e.g. "PDF Viewer & Inspector"
    val version: String
    val supportedExtensions: List<String> // e.g. ["pdf"]
    val hostSkeletonType: HostSkeletonType// TEXT, DOCUMENT, MEDIA, SPATIAL, WEBVIEW
    val isHeadlessOnly: Boolean

    // 1. Scoped AI Toolset
    fun getAvailableTools(): List<AiToolDefinition>
    suspend fun executeTool(toolName: String, params: Map<String, Any>): ToolResult

    // 2. Secondary On-Demand Visual UI
    @Composable
    fun RenderWorkView(
        file: File,
        onFileChange: (File) -> Unit,
        onAskAiAboutSelection: (SelectionContext) -> Unit
    )

    // 3. MCP Configuration (Optional)
    val mcpConfig: McpServerConfig?
}
```

---

## 5. Global Settings > General > Engines Management

Located in the app under **Global Settings > General > Engines**:

1. **Engine Registry & CRUD**:
   - Lists all installed native and custom engines.
   - Status toggles: `Active`, `Suspended`, `Disabled`.
   - Default file extension associations (e.g. map `.md` to Document Engine or Code Editor).
   - Create, edit, or delete engine profiles.
2. **Import & Export Packages**:
   - **Export**: Packages an engine into a standalone `.omniengine.zip` (containing `engine.json`, UI bundle, and tool definitions).
   - **Import**: Extracts a `.omniengine.zip` into `/files/engines/<id>/` and registers it in the database immediately.
3. **The AI Adaptation Lab (Meta-Development)**:
   - UI panel to audit imported open-source web components.
   - Runs an automated security scan (blocking external tracking scripts or unsandboxed storage access).
   - Injects the `window.OmnivianBridge` wrapper into the tool's HTML/JS bundle.
4. **Memory Hygiene & Lifecycle Monitor**:
   - Displays real-time RAM footprint per engine.
   - **Stateless Sleep**: Inactive engine views are automatically dumped from memory when switched away, persisting viewport coordinates and re-inflating in <100ms.

---

## 6. Strict AI Permission Guardrails (Cross-Engine Boundaries)

To guarantee the user maintains total control:
1. **Intra-Engine Operations**: While the user is working inside Engine X, the AI may freely execute Engine X's tools without interrupting the user.
2. **Cross-Engine Request**: If the AI determines that completing a prompt requires tools or files from Engine Y (or requires opening a new tab):
   - Execution is suspended.
   - The system displays a high-priority permission card:
     > 🛡️ **Engine Boundary Alert**  
     > *The AI in the Code Workspace is requesting access to the PDF Engine to extract netlist diagrams from `schematic.pdf`.*  
     > `[Grant Access for this Task]`  `[Deny]`
3. **No Unsolicited Tabs**: Tabs are visual viewports for humans. The AI cannot spawn visual tabs autonomously.

---

## 7. Implementation Roadmap

### Phase 1: Native Host Skeletons & Sandboxing
- [ ] Implement `WorkspaceStorageManager` providing isolated `/files/workspaces/<id>/` directories.
- [ ] Refactor the work screen into `WorkspaceShellScreen` with `WorkspaceFileExplorer` + `DynamicEngineHost`.
- [ ] Implement the 5 native host skeletons: Text, Document (PDF/Zip/MD), Media, Spatial, and WebView Sandbox.
- [ ] Build the AI Boundary Permission Guard (`request_engine_access`).

### Phase 2: Core "View First" Engines
- [ ] **Code Engine**: Syntax viewing + independent Diff Viewer.
- [ ] **PDF Engine**: Android `PdfRenderer` page scrolling + AI page text extraction.
- [ ] **Zip Engine**: In-memory archive inspector with virtual tree navigation.
- [ ] **Document Engine**: Markdown viewer + CSV Data Grid + Slide Deck Pager.
- [ ] **Image Engine**: Pan/zoom canvas + **Spatial Bounding Box** region selector for AI queries.
- [ ] **Browser Engine**: Sandboxed WebView with DOM scraper and local preview connection.

### Phase 3: Global Settings Management & AI Ingestion Lab
- [ ] Build **Global Settings > General > Engines** sub-page (CRUD + file associations).
- [ ] Implement `.omniengine.zip` import/export pipeline.
- [ ] Build the AI Adaptation Lab to scan, bridge, and register community web tools.

### Phase 4: Specialized Long-Term Engines
- [ ] Virtual Breadboard & Circuit Simulator (DC first).
- [ ] 3D Model Inspector (glTF / GLB via Filament or `<model-viewer>`).
- [ ] Map & GeoJSON Viewer (Leaflet / OSM vector tiles).
- [ ] Audio Waveform Trimmer & Video Timeline.
- [ ] Knitting & Stitch Craft Grid Engine.
