# Workspace and Extra Plans Architecture Plan
> **STATUS: NOT CHOSEN / SUPERSEDED / SKIPPED**
> This plan has been superseded by the simplified `/MODULAR_WORKSPACE_VIEWER_PLAN.md` (decoupling headless engine toolsets from user view/edit tabs). Keep for historical reference only.

---

## 1. Executive Summary & Core Philosophy

The Omnivian workspace architecture is structured strictly on a **File Explorer Foundation**. 

### Foundational Principles:
1. **The Core Backbone is the Storage & File Explorer**:
   - Every workspace/chat owns an isolated, sandboxed directory on internal Android storage: `/data/user/0/com.example/files/workspaces/<workspace_id>/`.
   - Files on disk are the single source of truth.
   - The File Explorer is the universal browser and manager across all file types (code, markdown, PDFs, images, zip archives, circuits, 3D assets).
2. **The Unit of Work is the "Workspace Tab"**:
   - An engine is fundamentally an internal pairing: **Engine = Viewer + Toolset**.
   - A **Workspace Tab** is an active instance of an engine mounted on a specific file or folder inside the workspace.
   - Users can open, switch, and close multiple Workspace Tabs side-by-side or via a swipeable tab strip.
3. **The Workspace Tab Layout (Direct Fit into Current Code Editor)**:
   - Built directly on top of the existing `CodeScreen.kt` pattern:
     - **Right Drawer**: Retains the existing `ModalNavigationDrawer` containing the **File Explorer** (`FileExplorerDrawerContent`). The File Explorer adapts its presentation and badges based on the active files (code syntax tags, image thumbnails, document icons).
     - **Main Work Area**: Replaces the static single-editor layout with a **Dynamic Tab Container**. The active tab swaps the viewer/editor according to the file extension (.kt/.py/.js -> Code, .md/.csv -> Document/Table, .pdf -> PDF Viewer, .png -> Canvas, HTML -> Webview).
     - **Bottom Carousel**: Slots in horizontally at the bottom of the active workspace view *specifically for workspaces that need it* (e.g. code git commits, canvas sketch takes, document drafts).
4. **The Universal Multimodal Artifact System & 3-Tier Progression**:
   - **The Non-Text Master Rule**: Every output from the AI is classified into exactly two categories:
     - **Pure Conversational Text** ➔ Standard chat message bubble.
     - **Everything Else (Media, Structured Assets, Interactive UI)** ➔ **Artifact**.
   - **Tier 1: Inline Artifacts & Hybrid Interactive Controls**:
     - *Visual & Code Cards*: Compact previews (code snippet, SVG swatch, mini-table, 3D thumbnail).
     - *Audio & Video Inline Cards*:
       - **Audio**: Visual waveform scrub-bar, play/pause toggle, duration stamp (`0:14 / 1:30`), playback speed (`1x`/`1.5x`).
       - **Video**: Aspect-ratio thumbnail with centered play overlay, inline player surface, and fullscreen expansion chip.
       - **Shared ExoPlayer Resource Pool (Android Lifecycle Safe)**: To prevent `MediaCodec.CodecException` and OOM crashes, a single shared ExoPlayer instance manages audio/video playback across the entire chat stream. Cards attach to the player on demand; only one media stream plays at a time.
     - *Hybrid Interactive Control Artifacts (Proactive UI Generation)*:
       - Rather than asking repetitive conversational questions (e.g. *"What volume, bitrate, and format do you want?"*), the AI serves proactive, dynamic in-chat control widgets.
       - Renders native Material 3 interactive components: **Sliders, Toggle Switches, Segmented Buttons, Dropdowns, Steppers, and Color Pickers**.
       - Interacting with controls dispatches a structured event directly back to the AI session (`{"action": "apply_settings", "gain": 80, "codec": "aac"}`), immediately closing the loop without requiring manual user typing.
   - **Tier 2: Full-Screen View-Only Artifact Dialog**:
     - **Strictly View-Only**: Artifacts are previews/outputs, not active code editors. Users can pan, zoom, scroll, read, scrub media, or test an interactive web app, but cannot directly edit file buffers.
     - **Top Bar Structure**:
       - `[Title]` (e.g. `invoice.pdf`, `calculator.html`, `track_stem.wav`, `render.mp4`, `route.geojson`).
       - `[Preview 🌐 | Code 💻 Toggle]` (specifically for web apps: toggles between live WebView preview and raw read-only syntax code view with copy).
       - `[💾 Save to Artifacts]` (**Restricted to Web Apps & Sites Only**): Only web apps / sites can be saved to the Global Sidebar "Artifacts" page library. Non-web artifacts (single PDF, image, video, audio, 3D file) belong in their workspace folders.
       - `[✏️ Edit ▾]` (Forking Bridge to Workspace Tab): Tapping Edit gives two choices:
         - *Option A (Current Chat)*: Writes the artifact file to current workspace storage (`files/workspaces/<id>/...`) and opens a matching new **Workspace Tab**.
         - *Option B (New Chat)*: Clones the artifact into a fresh workspace and opens a new chat with the corresponding Workspace Tab mounted.
       - `[🔄 Reload]` and `[✕ Close]`.
     - **View-Only Engine Mounting (Zero Redundant Code)**: The artifact dialog mounts the view-only mode of the corresponding Engine Host:
       - *Web Apps*: Sandboxed WebView (or code syntax viewer).
       - *Code files*: Syntax highlighted view + line numbers.
       - *Documents & Ebooks*: Native Android `PdfRenderer` or formatted Markdown reader.
       - *Images & Vectors*: Pan/zoom canvas.
       - *3D Assets (`.glb`, `.gltf`)*: Orbit/zoom 3D viewport (`<model-viewer>` or Filament).
       - *Spatial & Maps (`.geojson`, `.kml`)*: Interactive Leaflet / OpenStreetMap vector viewer.
       - *Audio & Video*: High-fidelity ExoPlayer surface with scrub bar, pitch/speed controls, and zoomable waveform visualizer.
   - **Tier 3: Workspace Tab**: Full, persistent work environment backed by real files on disk, supporting multi-file structures, bottom variation carousel takes, and two-way editing.

5. **New Chat Focus Dropdown**:
   - Updates the existing preset selector in `NewChatDialog.kt` to include workspace focus options (e.g., Code Project, Document & Writing, Data & Research, Visual & Canvas, Web Sandbox, 3D & Spatial).
6. **The "Knife-Set" Tool Model & AI Boundary Guardrails**:
   - **The Problem**: Giving an AI 40 tools simultaneously causes tool confusion, hallucinations, and token waste.
   - **The Knife-Set Rule**: Like specialized knife sets (chef's knife, paring knife, cleaver), each workspace tab has its own dedicated set of tools:
     - **Utility Knife (Global Tools - 2 to 3 only)**: Basic file peeking (`read_file_preview`), listing workspace files (`list_workspace_files`), and requesting tool set switches. Available across all workspaces.
     - **Chef's Knife (Code Workspace)**: AST refactoring, code diff patchers, compiler diagnostics.
     - **Cleaver (Document & Table Workspace)**: CSV SQL queries (SQLite in-memory queries, joins, aggregations), cell mutators, PDF page extractors, slide deck formatters.
     - **Media Slicer (FFmpeg ARM64 Media Workspace)**: Fast stream-copy trims (`-c copy`), loudness normalization (`loudnorm`), waveform generation (`showwavespic`), GIF generation, and frame extraction.
     - **Vector Compass (Diagram & Flowchart Workspace)**: Mermaid.js and Graphviz syntax verification, layout rendering, and SVG export.
     - **Precision Scalpel (3D & Spatial Workspace)**: OpenSCAD procedural CSG modeling, STL/3MF export, glTF mesh inspection, and GeoJSON coordinate calculations.
   - **Strict Permission Guardrail (No Silent Switching)**:
     - The AI is hard-blocked from using tools outside the active workspace knife set.
     - If the AI needs a tool from another set, it **MUST halt and request explicit user permission**:
       > 🔪 **Tool Knife-Set Switch Request**:  
       > *"To inspect coordinates in `locations.geojson`, I need the Spatial & Map tool set. Switch workspace focus to the Map Tab?"*  
       > `[Allow & Switch]` `[Deny]`
7. **Engine Composability & The AI Adaptation Lab**:

   - **Composability**: Viewer Skeletons and Toolsets are modular Lego bricks. Users can mix a Viewer from one engine (e.g. Canvas) with a Toolset from another (e.g. Circuit solver) to create custom hybrid engines.
   - **The AI Adaptation Lab**: A dedicated, specialized chat studio in the app equipped with security auditing, bridge auto-wiring (`window.OmnivianBridge`), and sandbox preview testing to adapt community open-source HTML5/Wasm web components into native Omnivian engines.
   - **Global Settings > General Integration**: Management and CRUD for engines live under the existing `GlobalSettingsScreen.kt` sub-page (`settings/general`), allowing users to create new engines, adjust file extension associations, and export/import `.omniengine.zip` packages.


---

## 2. File Explorer & Storage Architecture

### 2.1 Isolated Workspace Directories
Each workspace is completely sandboxed on Android storage:
```
/data/user/0/com.example/files/workspaces/
├── ws_project_alpha/
│   ├── .omnivian/
│   │   ├── workspace.json          <-- Archetype, allowed tools, active skills
│   │   └── history/                <-- Bottom carousel take snapshots
│   ├── src/
│   │   └── main.py
│   ├── notes.md
│   ├── spec.pdf
│   └── data.csv
├── ws_circuits_beta/
│   ├── .omnivian/
│   └── schematic.circuit
└── ws_portfolio_gamma/
    ├── .omnivian/
    ├── index.html
    └── style.css
```

### 2.2 Cross-Workspace File Operations
- Because the base is a unified File Explorer, users can copy, move, and drag-and-drop files or folders across different workspace directories seamlessly.
- Deleting or renaming a file in the File Explorer broadcasts an event to all open Workspace Tabs to prevent stale buffers.

---

## 3. The 3-Tier Interaction Pipeline

```
┌──────────────────────────────────────────────────────────────┐
│ Tier 1: In-Chat Artifact & Interactive Controls              │
│ • Everything non-text: Audio wave, video card, code, 3D card │
│ • Hybrid interactive widgets: Sliders, toggles, steppers     │
│ • Shared ExoPlayer pool: Single media stream active at once  │
│ • Action: [View Fullscreen] or Interactive UI Dispatch       │
└──────────────────────────────┬───────────────────────────────┘
                               │ Tap [View Fullscreen]
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Tier 2: View-Only Artifact Dialog (Engine Viewers)           │
│ • Strictly view-only (no direct code buffer editing)         │
│ • Web apps: [Preview | Code] toggle + [Save to Sidebar]      │
│ • Other media (PDF, Audio, Video, 3D, Map): View-only engine │
│ • Action: [Edit] ➔ Fork to Workspace Tab (Current or New ws) │
└──────────────────────────────┬───────────────────────────────┘
                               │ Tap [Edit]
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Tier 3: Workspace Tab                                        │
│ • Backed by persistent files on disk                         │
│ • Integrated with File Explorer & bottom variation carousel  │
│ • Specialized "Knife-Set" domain tools unlocked for AI       │
└──────────────────────────────────────────────────────────────┘
```

---

## 3.2 Workspace-Scoped Tool Namespaces & Foldable Accordion Architecture

### 1. Workspace-Bound Tool Allocation (Anti-Bloat & Zero Hallucination)
- Tools are not uniformly dumped into the global model context. Instead, tools are categorized into:
  - **Global Utilities (Always Active)**: Baseline file inspection, directory navigation (`view_file`, `list_dir`, `edit_file`).
  - **Workspace-Bound Tools**: Tools specifically scoped to an individual workspace or engine archetype (e.g. Code workspace gets AST/git/lint tools; Media workspace gets trim/waveform/remux tools; Data workspace gets SQLite FTS5/query tools).
- **Dynamic Prompt Filtering**: When the active workspace changes, the system injects *only* Global Tools + the active Workspace's specific tool definitions into the LLM system prompt.
  - **Zero Context Bloat**: Saves 1,500–3,000 tokens per turn by hiding irrelevant tool schemas.
  - **Zero Hallucination**: Prevents the model from attempting media remuxing or audio filtering while editing SQL tables.

### 2. Tools & MCP Directory: Foldable Accordion UI
- In the Tools Directory, Integrations tab, and Thread Settings, tools are visually structured as **collapsible/foldable accordion cards** (matching the MCP server architecture and `AiManagerPanelScreen.kt`):
  ```
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ TOOLS & MCPs DIRECTORY                                  [Search Tools 🔍]  │
  ├─────────────────────────────────────────────────────────────────────────────┤
  │                                                                             │
  │ ▼ 📂 GLOBAL UTILITIES (Always Active)                                  [3]  │
  │   ├─ ☑ View File (`view_file`)                                              │
  │   ├─ ☑ Edit File (`edit_file`)                                              │
  │   └─ ☑ List Dir (`list_dir`)                                                │
  │                                                                             │
  │ ▶ 💻 WORKSPACE: CODE PROJECT (ws_code_alpha)                           [4]  │
  │                                                                             │
  │ ▼ 🎬 WORKSPACE: MEDIA & DSP (ws_media_01)                              [3]  │
  │   ├─ ☑ Lossless Video Trim (`native_media_trim`)                            │
  │   ├─ ☑ Audio Waveform Gen (`audio_waveform`)                                │
  │   └─ ☐ Loudness Normalizer (`ffmpeg_loudnorm`)                              │
  │                                                                             │
  │ ▶ 📊 WORKSPACE: DATA & SQLITE (ws_data_finance)                        [2]  │
  │                                                                             │
  │ ▼ 🔌 MCP SERVER: GITHUB (External MCP Provider)                        [5]  │
  │   ├─ ☑ Push Commit Tree (`github.push_commit`)                              │
  │   ├─ ☑ Create PR (`github.create_pr`)                                       │
  │   └─ ☑ List Issues (`github.list_issues`)                                   │
  │                                                                             │
  └─────────────────────────────────────────────────────────────────────────────┘
  ```
- **Foldable Container Features**:
  - **Animated Chevron Accordion**: Smooth rotation (`animateFloatAsState`) on header click to expand/collapse tool lists.
  - **Active Tool Badge**: Displays `X/Y Active` per workspace/MCP server.
  - **Master Toggle**: Enable or suspend the entire toolset for that workspace or MCP provider in one tap.
  - **Granular Permissions**: Individual switches to set each tool to *Auto-Approve*, *Prompt Every Time*, or *Disabled*.

---

## 3.1 Standalone Artifact Micro-Apps & Built-In Tool Architecture

### 1. Standalone Execution (Independent of Omnivian Process)
- When web artifacts are saved to the **Global Sidebar "Artifacts" page**, they function as **standalone utility apps**.
- **Dedicated Android Activity (`StandaloneArtifactActivity`)**:
  - Saved artifacts launch in their own Android task stack (`Intent.FLAG_ACTIVITY_NEW_TASK` / `documentLaunchMode="always"`).
  - They appear as distinct, separate app cards in the Android Recent Apps / multitasking overview.
  - **Zero Background Reliance**: Users can swipe away or kill Omnivian completely; the artifact continues running in its hardened sandbox without needing the AI chat engine or Omnivian to stay open.

### 2. Client-Side Built-In Tools (No Host / Daemon Invocations)
- Rather than delegating operations to heavy background daemons or native C++ processes, the AI writes self-contained tool logic **directly into the artifact's own JavaScript/HTML/CSS code**:
  - **GitHub Push Aid**:
    - Embeds a micro zip engine (e.g. `fflate.min.js`, ~8KB) to decompress user-provided zip archives entirely in browser memory.
    - Preserves directory structures, calculates SHA-1 blob hashes, and pushes trees/commits directly via the standard GitHub REST API (`fetch()`) and OAuth tokens.
  - **Cloudflare Daily Dashboard**:
    - Pure client-side dashboard calling `https://api.cloudflare.com/client/v4/zones/...` via `fetch()`.
    - Renders responsive health graphs, DNS controls, and analytics directly with vanilla CSS/SVG.
  - **Google AI Studio / Workspace Helpers**:
    - Direct browser-based fetch calls to Gemini REST or Workspace endpoints using injected blind API keys.

### 3. Thread Settings Expansion: Integrations & Secrets Tabs
Accessed via the 3-dots menu in the bottom navigation ➔ **Workspace Actions Bottom Sheet** ➔ **Thread Settings**:
- `ThreadSettingsScreen.kt` expands its pill tab bar:
  `[Universal] [Agents] [Integrations 🔌] [Secrets 🔑] [Versions] [GitHub]`
- **Tab 1: Integrations (🔌)**:
  - Toggles and configurations for client-side tool presets:
    - **GitHub**: Client ID, OAuth App parameters, default target repo/branch.
    - **Cloudflare**: Account ID, Zone ID.
    - **Google AI Studio**: Gemini model presets & endpoint routes.
    - **Custom Webhook / REST API**: Base URL and standard headers.
  - When enabled, the AI prompt is informed of active integrations and instructed to write the corresponding client-side API calls into generated artifacts.
- **Tab 2: Secrets (🔑 - Blind Key-Value Manager)**:
  - Form field manager (`KEY_NAME`, `SECRET_VALUE`, `DESCRIPTION`).
  - Stored encrypted in local Android storage (KeyStore / SQLite).
  - Values are masked (`••••••••••••`) and never exposed in cleartext.

### 4. Blind Secret Injection Protocol (Google AI Studio Pattern)
To prevent API keys and credentials from being leaked, hallucinations, or committed to code repositories:
1. **Zero Raw Values in AI Context**:
   - The AI assistant **NEVER** sees the actual secret values.
   - The system prompt only provides metadata:
     > *"Active Secrets: `CLOUDFLARE_API_KEY`, `GITHUB_PAT`. Access them at runtime via `window.__SECRETS__.KEY_NAME`. Never output or hardcode raw credential values."*
2. **Runtime Injection in Hardened WebView**:
   - When the artifact opens (either in the preview dialog or as a standalone app), the Android WebView host intercepts initialization and injects an unreadable, non-enumerable, frozen object into the window scope:
     ```javascript
     Object.defineProperty(window, '__SECRETS__', {
       value: Object.freeze({
         CLOUDFLARE_API_KEY: "cf_...",
         GITHUB_PAT: "ghp_..."
       }),
       writable: false,
       configurable: false
     });
     ```
   - The artifact makes authorized `fetch()` calls:
     ```javascript
     fetch("https://api.cloudflare.com/client/v4/...", {
       headers: { Authorization: `Bearer ${window.__SECRETS__.CLOUDFLARE_API_KEY}` }
     });
     ```
3. **Domain-Scoped Content Security Policy (CSP)**:
   - When external integrations are enabled, the Hardened WebView dynamically opens `connect-src` *only* to verified service origins (e.g. `connect-src 'self' https://api.github.com https://api.cloudflare.com;`), blocking all arbitrary third-party tracking or data exfiltration.
4. **Sanitized Code Sharing & Export**:
   - Because raw secrets are never written into the artifact's HTML source on disk, users can safely export, backup, or share their artifact code without leaking personal access tokens.

---

## 3.3 Ecosystem Extensions: MCP HTTP, Global Agent Dashboard, Cloudflare Sandboxes & Desktop QR Bridge

### 1. Embedded MCP Server via Streamlined Streamable HTTP (Not Deprecated SSE)
- **Direct HTTP Streamable Transport**:
  - Deprecates legacy Server-Sent Events (SSE). Uses modern standard Model Context Protocol (MCP) over Streamable HTTP (JSON-RPC 2.0 POST with chunked streaming transfers).
- **Liveness & Lifecycle**:
  - The embedded HTTP server (powered by lightweight Netty/Ktor) is **only active when Omnivian is open in foreground**.
  - Listens on `127.0.0.1:8765/mcp`.
  - Exposes local workspace tools (`read_file`, `write_file`, `git_push`, `search_fts5`) to external desktop IDEs (Cursor, Claude Desktop, VS Code) via standard reverse proxy / Wi-Fi pairing.

### 2. Global Sidebar Agent & Tasks Dashboard (Cross-Chat Mission Control)
- **Universal Mission Control**:
  - Located directly in the Global Sidebar (`GlobalSidebar.kt`).
  - Aggregates ongoing, pending, and scheduled tasks across **all workspaces and chats**.
  - **Dashboard Cards & Metrics**:
    - **Active Agents**: Shows which agents are currently executing (e.g., `@CodeReviewer` in Chat A, `@DataAnalyzer` in Chat B).
    - **Active Background Tasks**: Progress bars, elapsed execution times, and one-tap `[Cancel]` buttons.
    - **Chat Link**: Tapping a task card navigates directly to the originating chat thread and highlights the corresponding tool bubble.
  - Backed by Room entity `AgentTaskEntity` with reactive `Flow<List<AgentTask>>` subscriptions.

### 3. Cloudflare Micro-Sandboxes vs. Heavy Compute (The Truthful Boundary)
- **Cloudflare Workers / D1 / KV (Lightweight Server Sandbox)**:
  - Used strictly for lightweight, asynchronous tasks that do not require the user's phone to stay on:
    - Daily scheduled webhooks and status health checks.
    - Cloudflare DNS, analytics, or uptime pollers.
    - Storing aggregated JSON summaries in Cloudflare KV / D1 for instant retrieval when Omnivian opens.
  - **The Strict Boundary**: Workers are **not** virtual machines. They have a 128MB RAM / 50ms CPU limit and cannot execute Docker, C++ compilers, or FFmpeg binaries.
- **GitHub Actions Runner (Heavy Compute Sandbox)**:
  - For tasks requiring true Linux compilation, heavy testing, or media rendering:
  - Dispatches `workflow_dispatch` events to GitHub Actions runners (8GB RAM, full Linux CLI), returning the built APK, video render, or bundled release asset to Omnivian via GitHub API.

### 4. Cross-Device Desktop Web Bridge (Secure QR Pairing via Cloudflare Relay)
- **Concept**:
  - Interact with Omnivian from a desktop web browser while the mobile app is open on the phone.
- **Protocol Flow**:
  1. **Ephemeral Key Generation**: Desktop web client (`omnivian.web.app`) generates an ephemeral AES-GCM-256 key and displays a pairing QR code containing the session ID and public key.
  2. **Zero-Trust Camera Scan**: Omnivian scans the QR code, establishing an End-to-End Encrypted (E2EE) WebSocket relay over Cloudflare Durable Objects. The Cloudflare server only routes encrypted ciphertext and cannot read messages, files, or credentials.
  3. **Selective Thread Permission**: The phone explicitly asks the user which workspaces to expose (e.g., `[☑ Project Alpha]`, `[☐ Personal Finance]`). Only user-whitelisted chats appear on the desktop screen.
  4. **Active Session Only**: Operates strictly while the mobile app is active, completely respecting Android power management.

---


---

## 4. Pre-Compiled Native Host Skeletons (Google Play Compliant)

To adhere strictly to Google Play's ban on Dynamic Code Loading (DCL), the APK ships with **5 pre-compiled native host skeletons**:

| Host Skeleton | Supported Formats | Primary View Pipeline |
|---|---|---|
| **1. Code & Text Skeleton** | `.kt`, `.py`, `.js`, `.ts`, `.json`, `.sh`, `.txt`, `.sql` | Fast text buffer, syntax tokens, line numbers, independent Diff split |
| **2. Document & Paged Skeleton**| `.md`, `.pdf`, `.slides.md`, `.zip` | Native Android `PdfRenderer`, virtual Zip tree inspector, Markdown pager |
| **3. Media & Graphics Skeleton**| `.png`, `.jpg`, `.svg`, `.webp`, `.mp4`, `.mp3`, `.wav` | Pan/zoom canvas, Media3/ExoPlayer surface, audio waveform canvas |
| **4. Spatial & 3D Skeleton** | `.gltf`, `.glb`, `.stl`, `.scad`, `.geojson`, vector tiles | Filament / `<model-viewer>` 3D canvas and Leaflet/OSM vector map |
| **5. Sandbox WebView Host** | User-imported HTML5/Wasm macro-engines | Hardened Android `WebView` + typed `WebMessageListener` RPC bridge |

---

## 4.1 Tier 1: Built-in Native Android Engines (0MB APK Bloat, Native Silicon)

All foundational editor and viewer operations use **native Android OS hardware and SDK primitives**. This guarantees zero input latency, zero web view overhead, 120 FPS rendering, and zero battery drain:

1. **Code Editor (`CodeEditorPane.kt`)**:
   - Built natively in Jetpack Compose (`BasicTextField` / custom text layout).
   - Instant response, zero typing lag, native text selection handles, fast syntax coloring via `AnnotatedString`.
2. **Document & Markdown Editor (`DocumentSkeleton.kt`)**:
   - Native Markdown rendering + Android `PrintedPdfDocument` & `PdfRenderer`.
   - 100% offline vector PDF creation and page bitmap rasterization without heavy headless browsers.
3. **Video Engine & Trimmer (`NativeMediaEngine.kt`)**:
   - Hardware silicon accelerated via `android.media.MediaExtractor`, `MediaMuxer`, and hardware `MediaCodec` + `ExoPlayer`.
   - Lossless video trimming, audio track stripping, and stream remuxing in <200ms with zero battery penalty.
4. **Image Editor (`SkiaCanvasRenderer.kt`)**:
   - Compose `Canvas` backed by Google Skia (`Path.op`), `android.graphics.Bitmap`, and GPU `ColorMatrix`.
   - Real-time 120 FPS cropping, scaling, rotation, color adjustments, and freehand drawing markup.
5. **Audio & Music Editor (`AudioSynthEngine.kt`)**:
   - Native `AudioTrack` PCM streaming + waveform visualizer amplitude extraction.
   - Sample-accurate playback, pure sine/synth audio generation, and low-latency audio scrubbing.
6. **Relational Data & Workspace Search (`SqliteWorkspaceEngine.kt`)**:
   - Android native SQLite + JSON1 and SQLite FTS5.
   - Deterministic SQL queries, CSV imports, and sub-millisecond BM25 keyword search across workspace files.

---

## 4.2 Tier 2: Extensible Modular WebView (Obsidian-Style On-Demand Plugins)

Instead of bundling heavy WebGL, 3D, and mapping libraries into a bloated, monolithic page, Omnivian treats the WebView as an **extensible, on-demand plugin host inspired by Obsidian**:

### 1. The Obsidian Modular Pattern:
- **Zero Idle Memory Footprint**: The base `stage.html` shell is ultra-lightweight (<10 KB). It loads **no** heavy 3D, map, or diagram scripts on initial boot.
- **On-Demand Dynamic Injection**: Modules are injected into the DOM **strictly on demand** when the user opens a corresponding file or when an AI response mounts that specific viewer:
  - **3D Engine Module (`spatial_3d`)**: Injects Three.js / `<model-viewer>` / OpenSCAD WASM *only* when a `.stl`, `.obj`, `.gltf`, or `.scad` file is active.
  - **Maps & Spatial Module (`interactive_maps`)**: Injects Leaflet.js / OpenLayers with offline OpenStreetMap vector tiles *only* when viewing `.geojson`, `.kml`, or `.gpx` coordinates (eliminates Google Play Services Maps SDK bloat and API key requirements).
  - **Diagrams & Flowcharts Module (`diagram_canvas`)**: Injects Mermaid.js / Graphviz SVG runtime *only* when rendering flowcharts or sequence graphs.
  - **Custom User Plugins**: Extensible architecture allowing JavaScript micro-bundles (`manifest.json` + `bundle.js`) to be mounted dynamically into the Stage root.
- **Immediate VRAM Cleanup**: When the user navigates away from a 3D or map tab, the module's WebGL context and canvas are torn down, freeing 60–100MB of RAM immediately.

### 2. Hardened Universal WebView Architecture & Security:

To support community engines, diagrams, and WebAssembly micro-tools without security risks or memory bloat, all web-based workspaces use a **single, recycled, hardened WebView**:

1. **Zero-Trust Origin Isolation (`WebViewAssetLoader`)**:
   - `allowFileAccess` and `allowContentAccess` are permanently set to `false` to block traversal of private app directories.
   - Bundled scripts (Mermaid.js, OpenSCAD WASM, Leaflet) are loaded via a virtual origin: `https://omnivian.internal/assets/` intercepted in memory via `shouldInterceptRequest`.
2. **Strict Offline Content Security Policy (CSP) with Dynamic Whitelist**:
   - **Default Offline Policy**:
     ```html
     <meta http-equiv="Content-Security-Policy" 
           content="default-src 'none'; script-src 'self' 'unsafe-eval' https://omnivian.internal; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'none';">
     ```
   - **Dynamic Domain-Scoped Opening**: When an artifact declares required external services (e.g. GitHub Push Aid, Cloudflare Dashboard), the WebView dynamically injects a domain-scoped whitelist (e.g., `connect-src 'self' https://api.github.com https://api.cloudflare.com;`) while blocking arbitrary third-party endpoints.
3. **Single Recycled Process (Stage & Shell Pattern)**:
   - Rather than creating separate `WebView` instances for every open tab (which exhausts Android RAM and triggers OOMs), Omnivian retains **one shared, backgrounded WebView**.
   - Tab switches call `window.OmnivianBridge.swapEngine(target)` to hot-swap runtimes in <10ms with zero process allocation overhead.
4. **Type-Safe RPC (`WebMessageListener`)**:
   - Uses Android's modern `WebViewCompat.addWebMessageListener` instead of legacy `@JavascriptInterface` reflection.
   - Coroutine-backed 3-second timeouts prevent long-running or looping scripts from freezing the Android UI thread.

---

## 5. Initial Core Workspace Tabs (View First Matrix)

| Workspace Tab | Underlying Skeleton | View First (Now) | AI Interactive (Next) | Full Editor (Future) |
|---|---|---|---|---|
| **Code** | Text Skeleton | Syntax viewing + line numbers + Diff viewer | AI code explanations, inline diff suggestions | Real-time code editor |
| **PDF** | Document Skeleton | Native `PdfRenderer` page scroll | Page selection ➔ AI text / diagram analysis | Annotations, form filling |
| **Zip Archive** | Document Skeleton | Virtual folder tree inspector | AI inspects archive ➔ selective extraction | Archive creation, file injection |
| **Document** | Document Skeleton | Rich typography Markdown rendering | Highlight text ➔ AI rewrite/summarize | Bi-directional rich text editor |
| **Table / Data** | Document Skeleton | CSV/TSV table grid with sorting | Tap row/col ➔ AI formula calculation (SQLite) | In-place cell editor |
| **Presentation** | Document Skeleton | Swipeable slide cards (`HorizontalPager`) | AI generates slides or updates slide 3 | WYSIWYG slide layout designer |
| **Media / Video** | Media Skeleton | ExoPlayer playback + waveform/filmstrip | AI Fast Trim (`-c copy`) + loudness normalization | Multi-track timeline & filtergraph editor |
| **Diagram** | Sandbox WebView | Zoomable Mermaid/Graphviz SVG canvas | AI updates node connections / flowcharts | Interactive drag-and-drop node graph |
| **3D & Spatial** | Spatial Skeleton | Filament / `<model-viewer>` orbit view | AI compiles OpenSCAD code into 3D mesh | Live parametric Code-CAD editor |
| **Image** | Media Skeleton | Pan/zoom visual viewport | **Spatial Bounding Box**: Drag box ➔ AI inspects region | Pencil, arrows, cropping, filters |
| **Browser** | Sandbox WebView | Lightweight web navigation & local preview | **DOM / Screenshot Capture**: One-tap AI context dump | Full developer tools & JS console |

---

## 6. Universal Engine Contract & Composability

An engine is an internal bundle:
```kotlin
interface OmnivianEnginePlugin {
    val id: String
    val displayName: String
    val version: String
    val supportedExtensions: List<String>
    val hostSkeletonType: HostSkeletonType // TEXT, DOCUMENT, MEDIA, SPATIAL, WEBVIEW
    val isHeadlessOnly: Boolean

    // 1. Scoped AI Toolset
    fun getAvailableTools(): List<AiToolDefinition>
    suspend fun executeTool(toolName: String, params: Map<String, Any>): ToolResult

    // 2. On-Demand Visual Viewer
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

### Engine Part Mixer:
Users or developers can mix and match components in `engine.json`:
- **Viewer**: Borrow any of the 5 Host Skeletons or an imported Web component.
- **Toolset**: Select from registered headless toolsets (e.g., Code Diff, Table Query, Circuit Netlist, Web Scraper).
- **Skills**: Bind domain-specific guidelines (e.g., electronic circuit guidelines, medical paper writing).

---

## 7. The AI Adaptation Lab (Dedicated Meta-Development Studio)

Accessed via **Global Settings > General > Engines > Adaptation Lab**:
- A specialized developer agent chat designed specifically to inspect, audit, and bridge community open-source web components.
- **Audit**: Performs static security analysis, blocking unauthorized telemetry or unsandboxed storage access.
- **Auto-Wiring**: Injects the `window.OmnivianBridge` API:
  - `window.Omnivian.loadFile()` ➔ Receives file buffer from Android storage.
  - `window.Omnivian.saveFile(data)` ➔ Writes changes back to disk.
  - `window.Omnivian.callTool(name, args)` ➔ Invokes headless background tools.
- **Test Harness**: Mounts the component in an isolated preview container right inside the lab chat for interactive user testing.
- **One-Tap Deploy**: Packages the tool into `/files/engines/<id>/` and immediately registers it in the File Explorer and Tab Manager.

---

## 8. Global Settings > General > Engine Modules Hub & Tools Interconnection

### 1. The Engine Modules Hub (Global Settings > General)
Under `GlobalSettingsScreen.kt` ➔ **General Settings**, a dedicated sub-destination **"Engine & Visual Modules" 🧩** serves as the central manager for all runtime visual engines:
- **Module Discovery & State Tabs**: `[All | Installed | Available Downloads]`.
- **Pre-Bundled vs. On-Demand**:
  - **Offline Essentials (Pre-bundled in APK assets)**: Leaflet Maps (~800KB), Mermaid.js (~1.2MB) available immediately offline.
  - **On-Demand Downloads**: Spatial 3D (Three.js + OpenSCAD WASM), Excalidraw, and specialized CAD or math visualizers downloaded as `.omniengine.zip` packages.
- **Card Actions & Inspection**:
  - Module Metadata: Version, disk footprint, runtime environment (WebView WebGL vs. Native Canvas), and file extension mappings (`.stl`, `.gpx`, `.scad`).
  - Controls: Master Enable/Disable toggle, Delete/Uninstall button, and Storage Cache Flush.
- **Local Import & Sideloading**:
  - `[📁 Import Module ZIP from Device]` allows offline sideloading on air-gapped or restricted devices.
  - Validates `manifest.json`, sanitizes assets, and unpacks into private app storage (`/files/engine_modules/<id>/`).

### 2. Bi-Directional Interconnection: Engine Hub ◄──► Tools Directory
Engines and tools are no longer siloed; they operate through a unified reactive registry (`EngineRegistry.kt`):
- **Installing an Engine Unlocks its Companion Tools**:
  - When an engine module is installed and enabled, its declared companion tools (e.g. `render_3d_preview`, `compile_scad_geometry`) are automatically registered in `ToolPermissionManager.kt` and surfaced to the AI prompt under the relevant workspace accordion with a badge: `[🧩 Provided by Spatial 3D Engine]`.
- **Missing Engine Prompts in Tools Directory**:
  - If a user browses the Tools Directory and selects a tool whose parent engine is uninstalled (e.g. `render_excalidraw_canvas`), the tool card displays an action pill: `[⚠️ Requires Excalidraw Engine — Tap to Install from Engine Hub]`.
  - Tapping navigates directly to the Engine Modules Hub with the target module highlighted for one-tap download.
- **Disabling an Engine Suspends its Tools**:
  - Toggling an engine OFF instantly removes its tool definitions from the LLM system prompt, preventing hallucinated tool calls while the engine runtime is inactive.

---

## 9. Functional Bundles Implementation Roadmap (Easy vs. Complex)

To transition the existing repository into the Workspace and Extra Plans architecture without breaking current chat flows, compiler stability, or database schemas, the implementation is structured into **decoupled functional bundles** categorized by execution complexity:

---

### 🟢 Bundle A: Additive Settings & Mission Control UI (Easy & Low Risk)
*Characteristics: Pure Compose UI additions, read-only Flow subscriptions, zero process crossing, zero blast radius.*

- [ ] **Engine Modules Hub (`GlobalSettingsScreen.kt` > General)**:
  - Add "Engine & Visual Modules" destination under General Settings.
  - Tabbed catalog `[All | Installed | Available Downloads]` displaying engine versions, disk footprints, and active toggles.
  - Sideload support: `[📁 Import Module ZIP from Device]` for offline manual installations.
- [ ] **Bi-Directional Engine ◄──► Tools Registry Connection (`EngineRegistry.kt` & `ToolPermissionManager.kt`)**:
  - Automatically register companion tools in the Tools Directory when an engine module is installed and enabled.
  - Missing-engine action pills in Tools Directory: `[⚠️ Requires Engine — Tap to Install]` with direct navigation to the installer card.
  - Disabling an engine module dynamically suspends its companion tools from the AI prompt.
- [ ] **Thread Settings Tabs Expansion (`ThreadSettingsScreen.kt`)**:
  - Add `INTEGRATIONS("Integrations 🔌")` tab (presets for GitHub, Cloudflare, Google AI Studio, custom REST webhooks).
  - Add `SECRETS("Secrets 🔑")` tab (encrypted key-value store with masked inputs `••••••••••••`).
- [ ] **Foldable Accordion Directory for Tools & MCPs (`ToolPermissionManager.kt` / Settings)**:
  - Collapsible cards grouping tools by Workspace, Engine Module, and MCP provider with animated chevron rotation (`animateFloatAsState`).
  - Active tool count badges (`X/Y Active`) and master enable/disable toggles.
- [ ] **Global Sidebar Agent & Tasks Dashboard (`GlobalSidebar.kt`)**:
  - Mission Control card aggregating active agents and running tasks across chats.
  - Backed by Room `AgentTaskEntity` with reactive `Flow<List<AgentTask>>` observation and one-tap jump-to-chat links.
- [ ] **Shared ExoPlayer Resource Pool (`ExoPlayerPool.kt`)**:
  - Singleton pool providing max 2 recycled `ExoPlayer` instances to stop `MediaCodec.CodecException` crashes across chat cards.

---

### 🟢 Bundle B: Zero-Cost Native Android Primitives & Base Editors (Easy & Standalone)
*Characteristics: Native Android SDK C/C++ primitives, 0MB APK bloat, strictly deterministic, independent utility singletons.*

- [ ] **Native Code Editor (`CodeEditorPane.kt`)**:
  - Jetpack Compose native editor (`BasicTextField`) with syntax coloring via `AnnotatedString`, native line numbers, zero typing latency, and zero webview overhead.
- [ ] **Native Document & Markdown Engine (`AndroidPdfCompiler.kt`)**:
  - Wrap `android.print.pdf.PrintedPdfDocument` and `PdfRenderer` for 100% offline vector PDF generation from Markdown/HTML and sub-millisecond page bitmap rasterization.
- [ ] **Native Video & Hardware Remuxer (`NativeMediaEngine.kt`)**:
  - Wrap `android.media.MediaExtractor`, `MediaMuxer`, and hardware `MediaCodec` for instant (<0.2s) lossless video trimming, audio track stripping, and stream remuxing directly on silicon.
- [ ] **Native Image & Skia Canvas (`SkiaCanvasRenderer.kt`)**:
  - Jetpack Compose Canvas wrapping Google Skia `Path.op`, `android.graphics.Bitmap`, and GPU `ColorMatrix` for 120 FPS cropping, scaling, color grading, and drawing markup.
- [ ] **Native Audio & Music Engine (`AudioSynthEngine.kt`)**:
  - Zero-latency `AudioTrack` PCM streaming for mathematical sound generation (pure sine waves, metronome cues, pink noise) with amplitude waveform extraction.
- [ ] **Native SQLite Workspace Engine (`SqliteWorkspaceEngine.kt`)**:
  - In-memory query execution and FTS5 full-text indexing via Android's built-in `SQLiteDatabase` for sub-millisecond BM25 keyword search across workspace files.

---

### 🔴 Bundle C: The Standalone Artifact, Obsidian-Style Extensible WebView & Blind Secrets Pipeline (Complex & Interconnected)
*Characteristics: Android Activity task management, WebMessageListener IPC, DOM script injection timing, dynamic security headers, on-demand plugin loader.*

- [ ] **Standalone Artifact Micro-Apps (`StandaloneArtifactActivity.kt`)**:
  - Register standalone Android Activity with `documentLaunchMode="always"` and `FLAG_ACTIVITY_NEW_TASK`.
  - Enables artifacts launched from the Sidebar to run completely detached from Omnivian without requiring the main app or chat session to remain open.
- [ ] **Universal View-Only Artifact Dialog (`UniversalArtifactDialog.kt`)**:
  - Evolve `PWAPreviewBottomSheet.kt` into a polymorphic, strictly view-only fullscreen modal supporting `WEB_APP`, `CODE_SNIPPET`, `AUDIO`, `VIDEO`, `DIAGRAM_SVG`, and `MODEL_3D`.
  - Top Bar actions: `[Preview 🌐 | Code 💻]` toggle (webapps only), `[💾 Save to Artifacts]` (saves to Global Sidebar, webapps only), and `[✏️ Edit ▾]` fork dropdown (`Fork to Current Chat Workspace` vs `Fork to New Chat Workspace`).
- [ ] **Hardened Universal WebView with Obsidian-Style Modular Loader (`HardenedWebViewManager.kt`)**:
  - Ultra-lightweight base shell (`stage.html` <10KB) with zero heavy libraries loaded on initial boot.
  - **On-Demand Module Loader (`OmnivianBridge.loadModule`)**:
    - **3D Engine Module (`spatial_3d`)**: Injects Three.js / `<model-viewer>` / OpenSCAD WASM *strictly on demand* when opening `.stl`, `.obj`, `.gltf`, or `.scad` files.
    - **Spatial & Maps Module (`interactive_maps`)**: Injects Leaflet.js with offline OpenStreetMap vector tiles *strictly on demand* when viewing `.geojson`, `.kml`, or `.gpx` coordinates (0 API keys, 0 Play Services bloat).
    - **Diagrams Module (`diagram_canvas`)**: Injects Mermaid.js runtime *strictly on demand*.
    - **Immediate VRAM Cleanup**: Destroys canvas/WebGL contexts and frees 60–100MB RAM immediately upon navigating away from the view.
  - Encapsulate Android `WebView` using `WebViewAssetLoader` mapped to virtual origin `https://omnivian.internal/assets/`.
  - Permanently lock `allowFileAccess = false` and `allowContentAccess = false` to prevent private storage directory traversal.
  - **Dynamic Domain-Scoped CSP Whitelist**: Offline default (`connect-src 'none'`), dynamically opened *only* to verified service origins when integrations are declared (`api.github.com`, `api.cloudflare.com`).
  - **Blind Secret Injection Protocol**: Android intercepts DOM initialization and injects encrypted secrets into an immutable, frozen `window.__SECRETS__` runtime object before user scripts execute.
  - Type-safe JSON messaging via `WebViewCompat.addWebMessageListener` with coroutine-backed 3-second timeouts.
  - Single recycled "Stage & Shell" instance with `window.OmnivianBridge.swapEngine()` hot-swapping.
- [ ] **In-Chat Non-Text Artifact Cards & Hybrid Controls**:
  - Update `ChatScreen.kt` message renderer so structured outputs render as compact interactive cards.
  - Native M3 sliders, steppers, toggles, and segmented buttons served in-chat by the AI for zero-friction parameter tuning.

---

### 🔴 Bundle D: Workspace Screen Elevation & Dynamic Tool Scoping (Complex & Interconnected)
*Characteristics: Code editor decoupling, horizontal multi-tab state management, workspace-scoped LLM prompt filtering.*

- [ ] **Container Refactoring (`CodeScreen.kt` ➔ `WorkspaceScreen.kt`)**:
  - Extract the core text editor from `CodeScreen.kt` into `CodeEditorPane.kt` without modifying file-saving or diff-split logic.
  - Create `WorkspaceScreen.kt` as the universal host container hosting a top open-files tab strip, the active Host Skeleton viewer, and the bottom variation carousel (`Take 1`, `Take 2`).
  - Route open files by extension: Code files ➔ `CodeEditorPane`, PDF/Markdown ➔ `DocumentSkeleton`, Images/Video ➔ `MediaSkeleton`, 3D/WASM ➔ `SpatialSkeleton` or `HardenedWebView`.
  - In `FixedBottomNav.kt`, evolve the tab label from `"Code"` to `"Workspace"` while maintaining the seamless two-tab toggle with `AppTab.CHAT`.
- [ ] **Workspace-Bound Tool Allocation & Prompt Filtering**:
  - Filter active tools passed to LLM calls based on the active workspace tab (Global Utilities + active workspace domain tools).
  - Enforce "Knife-Set" guardrails in `ToolPermissionManager.kt`: hard-block silent tool switches and trigger explicit user approval before changing active toolsets.

---

### 🔴 Bundle E: External Ecosystem Bridges & Sandboxes (Complex & Interconnected)
*Characteristics: Local HTTP server lifecycle, Cloudflare Worker Durable Objects, E2EE Web Crypto, cross-network pairing.*

- [ ] **Embedded MCP HTTP Server (`LocalMcpServer.kt`)**:
  - Lightweight Netty/Ktor server active **strictly while Omnivian is in the foreground**.
  - Listens on `127.0.0.1:8765/mcp` using standard Streamable HTTP (JSON-RPC 2.0 POST with chunked streaming transfers; no deprecated SSE).
  - Exposes local workspace tools (`read_file`, `write_file`, `git_push`, `search_fts5`) to desktop IDEs (Cursor, Claude Desktop) over local Wi-Fi / ADB.
- [ ] **Desktop QR Web Bridge (`DesktopWebBridge.kt`)**:
  - Ephemeral AES-GCM-256 key exchange via camera QR scan of `omnivian.web.app`.
  - End-to-End Encrypted (E2EE) WebSocket relay via Cloudflare Durable Objects (zero plaintext exposed to relay server).
  - Selective workspace permissions (user explicitly chooses which chats to expose to the desktop session).
- [ ] **Cloudflare Micro-Sandboxes vs. GitHub Actions Runner**:
  - Deploy lightweight Cloudflare Worker scripts for scheduled webhooks and daily health cron checks without phone reliance.
  - Integrate GitHub Actions `workflow_dispatch` runner for heavy Linux compute (ARM64 FFmpeg filters, OpenSCAD 3D compilation, APK builds).
