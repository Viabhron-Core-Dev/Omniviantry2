# Modular Engine & Multi-Workspace Architecture Plan
> **STATUS: NOT CHOSEN / SUPERSEDED**
> This draft plan has been superseded by the unified `/MASTER_WORKSPACE_TAB_PLAN.md`. Keep for historical reference only.

---


The Omnivian workspace architecture is evolving from a code-editor-only screen into an **Operating System-like Modular Engine Platform**. 

The fundamental philosophy is **Headless AI-First, UI-Second On-Demand**:
- **The Core Backbone is the Storage & File Explorer**: Every workspace has an isolated storage directory (`files/workspaces/<workspace_id>`). Files on disk are the single source of truth.
- **Engines are Modular Apps**: An engine is a pluggable toolset that the AI can call programmatically (e.g. edit, parse, query, transform).
- **UI Tabs are Secondary & On-Demand**: Visual screens (editors, canvases, viewers) exist primarily for human inspection, verification, and spatial/temporal guidance (e.g. "look at this area/frame").
- **AI Tab Permission Rule**: The AI operates headlessly via tools. The AI is **strictly forbidden** from opening, switching, or creating visual tabs without explicitly asking user permission first.
- **Initial Core Scope**: Implement only the base modular architecture with 4 lightweight engines: **Code** (with independent Diff view), **Document** (Markdown, Tables/Datasets, Slides), **Image** (Canvas, Region Selector, Markup), and **Browser** (lightweight web inspection, DOM scraping).
- **Self-Hosting / Meta-Development**: Future specialized engines (Circuits, 3D, Knitting, Audio, Maps) can be built or imported directly inside the app as lightweight HTML5/Wasm sandboxes and registered dynamically.

---

## 2. Architecture & File System Isolation

### 2.1 Workspace Storage Isolation
Each workspace is physically sandboxed in Android internal storage:
```
/data/user/0/com.example/files/workspaces/
├── ws_alpha_code/
│   ├── .omnivian/
│   │   ├── workspace.json          <-- Archetype, allowed tools, active skills
│   │   └── history/                <-- Carousel variation snapshots
│   ├── src/
│   │   └── main.py
│   └── README.md
├── ws_beta_research/
│   ├── .omnivian/
│   │   └── workspace.json
│   ├── notes.md
│   ├── data.csv
│   └── presentation.slides.md
└── ws_gamma_circuits/
    ├── .omnivian/
    └── circuit.json
```

### 2.2 Cross-Workspace File Bridge
Because all workspaces share the underlying `FileExplorer` foundation:
- Users can copy or move files and folders across workspaces using standard File Explorer CRUD operations or drag-and-drop.
- Each workspace maintains its own `.omnivian/workspace.json` specifying enabled engines, restricted tools, and domain-specific rules.

---

## 3. The 3-Tier Interaction Hierarchy

To maintain consistency across all media types, every engine follows a standard 3-tier lifecycle:

```
┌──────────────────────────────────────────────────────────────┐
│ Tier 1: In-Chat Artifact Bubble                              │
│ • Inline preview inside the chat message stream              │
│ • Compact cards (code snippet, mini-table, image thumbnail)  │
│ • "Expand" action button                                     │
└──────────────────────────────┬───────────────────────────────┘
                               │ (Tap "Expand")
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Tier 2: Full-Screen / Modal Artifact Sheet                   │
│ • Interactive inspection overlay                             │
│ • Zoom, pan, search, quick tweaks                            │
│ • "Open in Work Tab" / "Promote to Workspace" action button  │
└──────────────────────────────┬───────────────────────────────┘
                               │ (Promote / User Approves)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Tier 3: Dedicated Work Tab Engine                            │
│ • Full viewport replacing or augmenting the work area         │
│ • Bottom variation carousel (takes, diffs, draft versions)   │
│ • Direct two-way file synchronization with workspace storage │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Initial Core Engines (Lean Scope)

### Engine 1: Code Engine & Independent Diff Viewer
- **Archetype**: Source code (`.kt`, `.py`, `.js`, `.ts`, `.html`, `.css`, etc.).
- **Headless AI Tools**: `file_edit`, `replace_lines`, `read_file_range`, `grep_search`.
- **UI View (Secondary)**: 
  - Fast, responsive code editor with syntax highlighting and line numbers.
  - **Independent Diff Viewer**: Separated from raw code editing to provide clear side-by-side or unified before/after comparisons across carousel takes or git commits.
- **Bottom Carousel**: Commit checkpoints, undo revisions, and AI draft variations.

### Engine 2: Unified Document Engine (Text, Tables, Slides)
- **Archetype**: Structured documents (`.md`, `.txt`, `.csv`, `.tsv`, `.slides.md`).
- **Headless AI Tools**: `read_doc_outline`, `replace_section`, `update_table_row`, `query_csv`, `format_slide_deck`.
- **UI View (Secondary)**:
  - *Document Mode*: Rich Markdown viewer and editor with typography styling.
  - *Data Grid Mode*: Tabular spreadsheet view for CSV/TSV with editable cells, sorting, and row additions.
  - *Presentation Mode*: Compose `HorizontalPager` rendering `# Slide` sections as fullscreen swipeable slides.
- **Bottom Carousel**: Document draft revisions, table transformation takes, and slide deck thumbnail strip.

### Engine 3: Image & Visual Canvas Engine
- **Archetype**: Vector and raster visual assets (`.png`, `.jpg`, `.svg`, `.canvas`).
- **Headless AI Tools**: `generate_image`, `crop_image`, `get_image_metadata`, `annotate_region`.
- **UI View (Secondary)**:
  - Pan/zoom canvas viewport (`graphicsLayer` scale and translation).
  - **Spatial Region Selection**: Interactive bounding box selector allowing users to drag over any visual area and dispatch directly to AI: *"Check this region: why is this component misaligned?"*
  - Lightweight vector pencil/arrow markup layer.
- **Bottom Carousel**: Image generation variations, layer states, and sketch iterations.

### Engine 4: Lightweight Browser Engine
- **Archetype**: Web pages, local previews, and HTML artifacts (`.html`, URLs).
- **Headless AI Tools**: `fetch_url`, `search_web`, `extract_dom_text`, `evaluate_js`.
- **UI View (Secondary)**:
  - Sandboxed Android `WebView` with URL navigation bar, back/forward controls, and reload.
  - **"Send DOM / Viewport to Chat"**: Instant capture of current web view state into AI context for debugging.
  - Integration with local `PreviewServer` for instantaneous preview of workspace web projects.

---

## 5. Universal Engine Contract & Dynamic Plugin Architecture

### 5.1 The Kotlin Engine Interface
Every built-in or community engine conforms to a unified interface:

```kotlin
interface OmnivianEnginePlugin {
    val id: String
    val displayName: String
    val version: String
    val supportedExtensions: List<String>
    val isHeadlessOnly: Boolean

    // 1. Toolset Registration
    fun getAvailableTools(): List<AiToolDefinition>
    suspend fun executeTool(toolName: String, params: Map<String, Any>): ToolResult

    // 2. On-Demand Visual UI (Nullable if purely headless)
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

### 5.2 Dynamic Import & Meta-Development (Building Engines in App)
To adhere to Android Google Play policies (prohibiting dynamic `.dex` loading):
- **Web Component Sandboxing**: User-imported or AI-generated engines run as sandboxed HTML5/CSS/JavaScript or WebAssembly bundles inside an isolated WebView container.
- **Creation Flow**:
  1. User imports or asks AI to build an editor/tool (e.g. Circuit Simulator, 3D glTF Viewer, Knitting Chart Grid).
  2. AI writes the bundle into `/files/engines/<engine_name>/` (`index.html` + `engine.json`).
  3. App `EngineManager` detects and registers the new engine.
  4. Two-way bridge (`window.Omnivian.loadFile()`, `window.Omnivian.saveFile()`, `window.Omnivian.callTool()`) connects the web UI to Android storage.

---

## 6. AI Agent Protocol & Tab Permission Guardrails

To preserve user focus and prevent autonomous visual disruptions:
1. **Headless Execution by Default**: The AI runs file tools, queries, and background processes silently.
2. **Explicit Permission Required for Visual Tabs**:
   - If an AI operation produces a visual artifact that warrants opening a new tab, the AI must invoke `request_open_tab(file, engine)`.
   - The UI surfaces a confirmation prompt to the user:
     > 🔔 **AI Workspace Notice**: *"I have generated a new presentation deck `overview.slides.md`. Would you like me to open it in the Document Engine tab?"*
     > `[Open Tab]`  `[Keep in Background]`
   - No tab is ever opened autonomously without user consent.
3. **Context Referencing (Spatial & Temporal)**:
   - When a user highlights text in the Document Engine, marks a region in the Image Engine, or pauses a frame, the coordinates/selection are passed into the AI prompt as structured context:
     ```json
     {
       "source_engine": "image",
       "file": "schematic.png",
       "bounding_box": [120, 45, 300, 180],
       "user_query": "Is this trace wide enough?"
     }
     ```

---

## 7. Global Settings > Engines Management

Under **Global Settings > Engines**, a dedicated management interface allows:
1. **Installed Engines**:
   - Toggle engines On/Off per workspace or globally.
   - Set default engine associations per file extension (e.g. `.md` opens in Document Engine vs. Code Editor).
2. **Model Context Protocol (MCP) Hub**:
   - Configure local or remote MCP servers (command-line stdio or SSE endpoints).
   - Dynamically expose MCP tools to relevant workspaces.
3. **Engine Store / Community Imports**:
   - Add new engine packages from a local folder or verified web bundle.
4. **Memory Hygiene & Lifecycle Control**:
   - View active memory footprint per engine.
   - Automatic teardown of inactive tab WebViews upon backgrounding to guarantee zero OOM crashes on Android devices.

---

## 8. Phased Implementation Roadmap

### Phase 1: Foundation & Base Modular Shell
- [ ] Decouple `CodeScreen` into `WorkspaceShellScreen` containing `WorkspaceFileExplorer` + `DynamicEngineHost`.
- [ ] Create `OmnivianEnginePlugin` registry and lifecycle manager.
- [ ] Implement per-workspace directory sandboxing and `.omnivian/workspace.json`.
- [ ] Implement AI Tab Permission guardrail (`request_open_tab`).

### Phase 2: Core 4 Engines
- [ ] **Code Engine**: Refactor existing editor into engine plugin + separate standalone Diff Viewer.
- [ ] **Document Engine**: Markdown, Data Grid (CSV/TSV), and HorizontalPager Slide Deck view.
- [ ] **Image Engine**: Pan/zoom canvas + interactive Bounding Box Region Selector for AI queries.
- [ ] **Browser Engine**: Lightweight sandboxed WebView with DOM scraping and local preview server link.

### Phase 3: Global Engine Manager & MCP Integration
- [ ] Build **Global Settings > Engines** management screen.
- [ ] Implement HTML5/Wasm Sandboxed Engine Loader (`/files/engines/`).
- [ ] Wire Model Context Protocol (MCP) tool registry into workspace context.

### Phase 4: Future Specialized Engines (Distant Horizon)
- [ ] Virtual Breadboard & Circuit Engine (DC first).
- [ ] Knitting & Stitch Craft Grid Engine.
- [ ] 3D Model Viewer Engine (`<model-viewer>` / Filament).
- [ ] Map & GeoJSON Engine.
- [ ] Multi-Modal Learning Canvas.
