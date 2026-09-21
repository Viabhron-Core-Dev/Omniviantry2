# Modular Workspace Viewer & Multi-Root Headless Architecture Plan
> **STATUS: ACTIVE & CHOSEN PLAN**
> Comprehensive architectural specification integrating all user-approved requirements: Multi-Root Chat Storage, 2-Tier Artifact System, Interactive Mild-Edit & Mark-to-Chat Viewers, Foldable Tool/Engine Directory, Native Android Hardware Acceleration, Hardened Webview Runtime Base, and Standalone Activity Multitasking.
> Supersedes all prior workspace plans (`WORKSPACE_AND_EXTRA_PLANS.md`, `MASTER_WORKSPACE_TAB_PLAN.md`, `MULTI_WORK_TAB_ENGINE_PLAN.md`, `MODULAR_ENGINE_WORKSPACE_PLAN.md`, and `MACRO_ENGINE_HOST_PLAN.md`).

---

## 1. Architectural Foundations: Multi-Root Storage & Clean Decoupling

### 1.1 The Workspace Tab as a Pure User View & Edit Surface
- The Workspace Tab does **not** govern which tools the AI is allowed to invoke.
- It is a human inspection, mild editing, and spatial markup canvas backed by a unified multi-root file explorer.
- The user can freely inspect an image, listen to an audio track, or read a PDF while the AI executes code or database tasks in the background without tab-switching interruption.

### 1.2 Multi-Root Workspace Storage (Per Chat)
Each chat session owns a parent storage container partitioned into semantic root folders:
```
/data/user/0/com.example/files/workspaces/chat_<chat_id>/
├── 🏠 default/ (or main_code/)      <-- Primary workspace root (Code Editor "Home")
├── 🖼️ images/                       <-- AI image generations, logos, canvas exports
├── 🎬 media/                        <-- Video clips, audio stems, raw recordings
├── 📑 docs/                         <-- PPTX presentations, markdown notes, PDFs
├── 📊 data/                         <-- SQLite databases, CSVs, JSON tables
└── 📦 extracts/                     <-- Decompressed ZIP archives & external templates
```

#### Storage & Explorer Behaviors:
1. **Unified File Explorer View**:
   - The File Explorer treats the entire container (`chat_<chat_id>/`) as one unified filesystem.
   - Root folders feature category badges (🏠 Code, 🖼️ Images, 🎬 Media, 📑 Docs, 📊 Data).
   - Breadcrumb navigation enables seamless navigation across roots.
2. **Code Editor Default Root**:
   - Defaults to `default/` (or `main_code/`) as its working "Home", but allows quick-switching via a root picker dropdown.
3. **Cross-Root Operations (Move, Copy, Replace)**:
   - Users can drag, copy, or move files between roots (e.g. move a generated logo from `images/logo.png` to `default/public/icon.png`).
   - If a file exists at the target, the system prompts: `[Replace]` or `[Keep Both]`.
   - Modifying or replacing a file broadcasts an invalidation event to all active viewers to prevent stale buffers.

---

## 2. Universal Artifact System & 2-Tier Progression

### 2.1 The Non-Text Master Rule
- **Pure Conversational Text** ➔ Rendered as standard message bubbles.
- **Everything Else (Non-Text)** ➔ **Artifact**.
  - Code files, HTML webapps, images, audio waveforms, video clips, presentations, and data tables.
  - Non-text items automatically route into their designated chat root folder (`images/`, `media/`, `docs/`, etc.).

### 2.2 Tier 1: In-Chat Mini Artifacts & Hybrid Controls
- **Inline Mini Previews (Unfolded in Chat Stream)**:
  - Visual cards (code snippet with copy, SVG preview, mini-table).
  - Audio waveform scrub-bar with inline play/pause and timestamp (`0:14 / 1:30`).
  - Video player surface with inline playback and fullscreen chip.
  - Interactive parameter widgets (M3 sliders, toggles, steppers, segmented buttons) served directly by the AI for instant tuning.

### 2.3 Tier 2: Fullscreen Preview Dialog (Polymorphic Bottom Sheet)
- Tapping an artifact card opens the fullscreen inspection modal.
- **Top Bar Structure**:
  ```
  ┌────────────────────────────────────────────────────────────────────────┐
  │ [Title: invoice.pdf]  [Preview 🌐 | Code 💻]  [💾 Save]  [✏️ Edit ▾]  [✕] │
  └────────────────────────────────────────────────────────────────────────┘
  ```
  - `[Preview 🌐 | Code 💻]`: Toggles between live rendering and raw syntax code (web apps only).
  - `[💾 Save to Artifacts]`: **Restricted strictly to interactive web apps & PWAs**. Single media files (PDF, image, audio, video) belong in workspace root folders and cannot be saved to the global library.
  - `[✏️ Edit ▾] (Forking Bridge)`:
    - **Option A (Edit in This Chat)**: Creates or mounts a dedicated root folder in the current chat and opens the workspace editor.
    - **Option B (Fork to New Chat)**: Clones the artifact into a brand new chat session with a fresh storage container.
  - `[✕ Close]`: Dismisses the modal.

### 2.4 Global Sidebar "Artifacts" (Mini-Apps) Library
- Saved mini-apps display in the Global Sidebar Artifacts page with:
  - App icon/badge and thumbnail preview.
  - App title, category tag, and last modified timestamp (`MMM d, yyyy · HH:mm`).
- **Long-Press Context Menu (Full CRUD + Fork)**:
  - ✏️ Rename Title
  - 🎨 Edit in Workspace
  - 🍴 Fork (Clone to new standalone artifact copy)
  - 📌 Pin to Top
  - 🗑️ Delete Artifact

---

## 3. Interactive Pluggable Viewers: Mild Edit & "Mark-to-Chat"

Viewers are **not** passive read-only screens. They provide direct user editing and spatial markup piped back into chat.

### 3.1 Pluggable Viewer Matrix & "Open With..." Routing

| Viewer Type | Extensions | Primary Engine / Component | User Actions & Markup |
| :--- | :--- | :--- | :--- |
| **Code / Text** | `.kt`, `.py`, `.js`, `.ts`, `.json`, `.xml`, `.sh`, `.txt`, `.c`, `.cpp` | `TextViewer.kt` / `CodeEditorPane.kt` | Syntax highlighting, line numbers, mild editing, save, diff toggle. Highlight text ➔ `[💬 Ask AI about selection]`. |
| **Presentation (PPT)** | `.ppt`, `.pptx`, `.slides.md` | `PptViewer.kt` (Compose `HorizontalPager`) | Slide deck navigation, reorder/add slides, mild text/bullet edit. AI slide parameter sliders (count, density, layout). |
| **Document** | `.pdf`, `.docx`, `.md` | `PdfViewer.kt` (Android `PdfRenderer`) | Sub-millisecond vector rendering, text selection ➔ `[💬 Ask AI to rewrite/fix clause]`. |
| **Image & Canvas** | `.png`, `.jpg`, `.jpeg`, `.webp`, `.svg`, `.gif` | `ImageViewer.kt` (Skia GPU Canvas + Coil) | Pinch-to-zoom, pan, rotation. Spatial Bounding Box selection ➔ `[💬 Replace / Edit region]`. |
| **Video Player** | `.mp4`, `.mkv`, `.webm`, `.mov` | `VideoViewer.kt` (Media3 / ExoPlayer) | Play/pause, scrub slider, timestamp, aspect fit. Lossless trim marker. |
| **Audio Player** | `.mp3`, `.wav`, `.m4a`, `.aac`, `.ogg` | `AudioViewer.kt` (Android `MediaPlayer`) | Play/pause, waveform scrubber, time stamp. |
| **Webview Runtime** | `.html`, `.htm` | `WebviewViewer.kt` (Hardened Android `WebView`) | Sandboxed execution, console log panel, live preview / source code toggle, blind secrets injection. |

### 3.2 "Mark-to-Chat" Protocol (Last Push by User)
- Highlighting code/document text or drawing a bounding box on an image presents a floating action pill.
- Tapping the action inserts the file path, line numbers / bounding coordinates, and context snippet directly into the **Chat Input Text Box**.
- **User Total Control**: The text is staged in the input field. The user can review, append instructions, and presses the Send button themselves.

### 3.3 File Explorer "Open With..." Context Menu
Inside `FileExplorer.kt`, long-pressing or tapping the 3-dot menu on any file displays:
```
┌────────────────────────┐
│ file_name.ext          │
├────────────────────────┤
│ ✏️ Rename              │
│ 📋 Copy to Root...     │
│ 📦 Move to Root...     │
│ 🗑️ Delete              │
│ 🚀 Open With...       │
│   ├─ 💻 Code Editor   │
│   ├─ 📽️ Slide Deck    │
│   ├─ 📄 Document      │
│   ├─ 🖼️ Image Markup  │
│   ├─ 🎬 Video Player  │
│   ├─ 🎵 Audio Player  │
│   └─ 🌐 Webview       │
└────────────────────────┘
```

---

## 4. Headless Engines & Foldable Accordion Tools Directory

### 4.1 Engines as Tool Bundles
- Engines are **headless toolsets** located in `com.example.engine.*`.
- No separate "Engine Settings" screen is needed. All engines and tools live inside the **Tools Directory** (Global Settings and Thread Settings).

### 4.2 Foldable Accordion UI (Folded by Default)
Tools are grouped cleanly into collapsible cards that are collapsed by default:
```
┌────────────────────────────────────────────────────────┐
│ TOOLS & ENGINES DIRECTORY                 [Search 🔍]  │
├────────────────────────────────────────────────────────┤
│ ▶ 📂 Global Core Utilities (view_file, edit_file)  [3] │  <-- Folded
│ ▶ 💻 Code & AST Engine (patch, lint, compile)      [4] │  <-- Folded
│ ▶ 📊 SQLite & Data Engine (FTS5 search, query)     [3] │  <-- Folded
│ ▶ 🎬 Native Media Engine (Video Trim, Waveform)    [3] │  <-- Folded
│ ▶ 📽️ Presentation & Document Engine (PPT/PDF)      [2] │  <-- Folded
│ ▶ 🌐 Webview & DOM Engine (DOM scrape, JS sandbox) [6] │  <-- Folded
│ ▶ 🔌 MCP: GitHub Integration (commit, PR, issues)  [5] │  <-- Folded
│ ▶ 🔌 MCP: Cloudflare Sandbox (Worker deploy, cron) [2] │  <-- Folded
└────────────────────────────────────────────────────────┘
```
- **Unfolding**: Clicking any accordion header smoothly expands its tool switches, auto-approve permissions, and parameter descriptions.
- **Dynamic Registration**: When an engine or MCP server is loaded, its tools automatically register into a new folded group.

---

## 5. Native Android Hardware Acceleration & Hardened Webview Base

### 5.1 Native Android Hardware Primitives (0MB Extra APK Bloat)
1. **Video & Audio Hardware (`android.media.MediaExtractor`, `MediaMuxer`, `MediaCodec`)**:
   - Hardware-accelerated lossless video trimming, audio track stripping, and stream remuxing.
2. **AudioTrack PCM Sound Engine**:
   - Real-time mathematical sound synthesis (sine waves, metronome cues, test tones) and waveform generation.
3. **Native Skia GPU 2D Canvas (`android.graphics.Bitmap`, `Path.op`, `ColorMatrix`)**:
   - 120 FPS pinch, pan, zoom, crop, and spatial bounding box selection.
4. **Native PDF Engine (`android.graphics.pdf.PdfRenderer`, `PrintedPdfDocument`)**:
   - Instant page rasterization and vector PDF generation from Markdown/HTML.
5. **In-Memory SQLite Engine (`android.database.sqlite`)**:
   - Full SQL queries against CSV/DB files and FTS5 full-text indexing across workspace files.
6. **Native Archive Engine (`DocumentAndArchiveTools.kt`)**:
   - Full ZIP decompression, packing, and content peeking in workspace root folders.

### 5.2 Hardened Universal Webview as Pluggable Extension Base
- **Obsidian / VS Code Web Model**: Lightweight stage shell (`stage.html` <10KB) with zero heavy libraries on boot.
- **On-Demand Module Injection**:
  - Injects Three.js / `<model-viewer>` WASM only when opening 3D files (`.gltf`, `.stl`, `.obj`).
  - Injects Leaflet.js with offline OpenStreetMap vector tiles only for spatial files (`.geojson`, `.kml`).
  - Injects Mermaid.js only for diagram files (`.mermaid`).
- **Immediate VRAM Cleanup**: Destroys canvas/WebGL contexts and frees 60–100MB RAM immediately when navigating away.
- **Security & Blind Secrets Protocol**:
  - `allowFileAccess = false`, `allowContentAccess = false`.
  - Offline default CSP (`connect-src 'none'`).
  - Encrypted keys injected into immutable, frozen `window.__SECRETS__` before scripts execute. AI never sees cleartext keys.

---

## 6. Ecosystem & Cloud Bridges

1. **Standalone Android Activity Task Stacks (`StandaloneArtifactActivity.kt`)**:
   - Saved web apps open in independent Android task stacks (`documentLaunchMode="always"` / `FLAG_ACTIVITY_NEW_TASK`).
   - Appears as distinct cards in Android Recent Apps / multitasking overview; survives swiping away Omnivian.
2. **Desktop QR Web Bridge (`DesktopWebBridge.kt`)**:
   - Ephemeral AES-GCM-256 key exchange via camera QR scan of `omnivian.web.app`.
   - E2EE WebSocket relay via Cloudflare Durable Objects.
3. **Cloudflare Worker Sandboxes & GitHub Actions**:
   - Deploy micro-worker scripts for scheduled webhooks and daily cron tasks.
   - GitHub Actions `workflow_dispatch` runner for heavy Linux build compute.

---

## 7. Implementation Roadmap

### 🔹 Phase 1: Multi-Root Storage & File Explorer "Open With..."
- Add semantic root folder support (`default/`, `images/`, `media/`, `docs/`, `data/`, `extracts/`) in `ArtifactWorkspaceManager.kt`.
- Update `FileExplorer.kt` with root navigation pills, cross-root move/copy actions, and "Open With..." context menu.
- Add `ViewerType` routing (`CODE`, `PPT`, `DOCUMENT`, `IMAGE`, `VIDEO`, `AUDIO`, `WEBVIEW`).

### 🔹 Phase 2: Interactive Pluggable Viewers & "Mark-to-Chat"
- Implement `ImageViewer.kt` (Skia GPU pan/zoom + bounding box markup ➔ Chat Input).
- Implement `PptViewer.kt` (HorizontalPager slide deck + text edit + AI parameter sliders).
- Implement `AudioViewer.kt` & `VideoViewer.kt` (Media3/ExoPlayer playback, waveforms, scrubbers).
- Implement `WebviewViewer.kt` (Hardened web runtime + console + blind secrets).
- Connect spatial/text selection action pills to populate the Chat Input text field.

### 🔹 Phase 3: Artifact Lifecycle & Global Sidebar Library
- Update inline chat rendering for Tier 1 mini-artifacts (waveforms, video player, interactive sliders).
- Polish Tier 2 Fullscreen Preview modal top bar: `[Preview | Code]`, `[Save to Artifacts]` (web only), `[Edit ▾]` (this chat root vs new chat).
- Update `ArtifactsScreen.kt` with app icons, thumbnails, and long-press CRUD (Rename, Edit, Fork, Pin, Delete).

### 🔹 Phase 4: Tools & Engines Directory (Foldable Accordions)
- Implement animated accordion cards in Tools Directory (Global Settings & Thread Settings), folded by default.
- Wire native hardware tools: SQLite in-memory FTS5, media remux/trim, and ZIP extraction.
- Wire MCP integrations for GitHub and Cloudflare sandboxes.

### 🔹 Phase 5: Standalone Activity & Ecosystem Bridges
- Register `StandaloneArtifactActivity` with `documentLaunchMode="always"`.
- Implement Desktop QR Web Bridge with E2EE WebSocket relay.
