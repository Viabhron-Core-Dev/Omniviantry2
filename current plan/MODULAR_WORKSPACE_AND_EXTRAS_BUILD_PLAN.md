# MODULAR_WORKSPACE_AND_EXTRAS_BUILD_PLAN.md
> **STATUS: ACTIVE MASTER SPECIFICATION & BUILD ROADMAP**
> Consolidates `MODULAR_WORKSPACE_VIEWER_PLAN.md` and `WORKSPACE_AND_EXTRA_PLANS.md` into a single, clean, executable master roadmap.
> Designed for safe, sequential implementation across 5 decoupled Mini-Phases.
> Every phase integrates `LogKeeper` telemetry and `BackupManager` AES-256-GCM encrypted persistence.

---

## 1. Executive Summary & Core Architecture

The Omnivian workspace architecture bridges the **Chat Experience** (conversational intelligence) and the **Workspace Tab** (human inspection, mild editing, and spatial markup):

1. **The 2-Tab Navigation Model (`FixedBottomNav.kt`)**:
   - `AppTab.CHAT`: Conversational stream, multimodal inputs, inline preview cards, and proactive parameter controls.
   - `AppTab.CODE` (Workspace): Multi-root file explorer, pluggable native viewers, mild editing, and "Mark-to-Chat" spatial feedback.
2. **The "Non-Text Master Rule"**:
   - **Pure Text** ➔ Standard message bubble.
   - **Non-Text (Media, Code, PPT, 3D, PDF, Web)** ➔ **Artifact**.
3. **The 3-Tier Artifact Lifecycle & Viewing vs. Editing/Interact Duality**:
   - **Tier 1 (In-Chat Inline Card & Modal Preview)**: **VIEW-ONLY**. Fast, frictionless preview within conversational context. Strictly consumption/inspection with zero inline editing friction. Includes instant 1-tap fullscreen inspection or "Workspace" pivot.
   - **Tier 2 (Polymorphic Bottom Sheet)**: Claude-style fullscreen sheet with live preview, code toggle, and fork-to-workspace actions.
   - **Tier 3 (Workspace Tab - View, Edit & Interact)**: Persistent multi-root filesystem on disk (`files/workspaces/<id>/`). When an artifact opens in Workspace:
     - **Mode Switcher Bar**: Seamless 1-tap toggle between **[ View / Preview ]** (interactive WebView, audio scrubber, Skia image viewer, PPT slides) and **[ Edit / Raw / Interactive Controls ]** (source markup, parameters, or text content).
     - **"Pass to AI" Button**: Instant bridge action in the Workspace top bar that stages the active artifact path and context chip into the Chat composer (`@artifact/<path>`) so users can ask AI to refactor, recolor, optimize, or modify the asset without manual copying.
4. **Mandatory Subsystem Standards**:
   - **LogKeeper**: Connected to all view transitions, player lifecycles, and tool dispatches. No credentials, tokens, or PII.
   - **Encrypted Backup & Restore**: Any newly introduced Room tables, settings, or provider keys automatically serialize into `BackupManager.kt`.
   - **Zero-Cost Silicon First**: Leverage native Android hardware APIs (`MediaCodec`, `MediaMuxer`, `PdfRenderer`, Skia GPU `Canvas`) before external webviews.

---

## 2. The 5 Executable Mini-Phases

```
┌────────────────────────────────────────────────────────────────────────┐
│ Mini-Phase 1: In-Chat Non-Text Artifact Cards & Shared Media Pool      │
│ • ExoPlayerPool singleton (no audio codec collisions)                  │
│ • Tier-1 Chat inline cards for code, HTML, audio, video, PPT, 3D, PDF  │
│ • LogKeeper component logging                                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Mini-Phase 2: Polymorphic Bottom Sheet & Standalone Multitasking       │
│ • UniversalArtifactSheet (HTML/JS, Document, Slides, Audio, Video, 3D) │
│ • Top bar: [Preview|Code], [Save to Artifacts], [Edit in Workspace]    │
│ • StandaloneArtifactActivity (documentLaunchMode="always" task stack)  │
│ • BackupManager integration for saved artifacts                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Mini-Phase 3: Native Zero-Cost Silicon Engines & Workspace Viewers     │
│ • ImageViewer.kt (Skia GPU 120 FPS pan/zoom + bounding box markup)     │
│ • PptViewer.kt (HorizontalPager slide deck + mild edit)                │
│ • StageWebViewer.kt (Lightweight <10KB shell for 3D glTF/STL & Maps)   │
│ • FileExplorer "Open With..." dynamic viewer dispatch                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Mini-Phase 4: Foldable Accordion Tools Directory & Thread Settings     │
│ • Animated foldable accordions (Global Utilities, Workspaces, MCPs)    │
│ • ThreadSettingsScreen expansion: Integrations (🔌) & Secrets (🔑) tabs│
│ • AES-256-GCM backup/restore of thread secrets and custom tools        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Mini-Phase 5: In-Chat Interactive Controls & "Mark-to-Chat" Bridge     │
│ • Proactive in-chat M3 parameter widgets (sliders, toggles, steppers)  │
│ • Mark-to-Chat: Selection pill stages coordinates/text in Chat input   │
│ • Complete system audit, test verification & receipts logging          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Mini-Phase 1: In-Chat Non-Text Artifact Cards & Shared Media Pool
**Target**: Tier-1 In-Chat representation of all non-text outputs without audio hardware crashes.

### Deliverables:
1. **`ExoPlayerPool.kt`**:
   - Location: `app/src/main/java/com/example/engine/media/ExoPlayerPool.kt`
   - Lifecycle-safe singleton managing up to 2 recycled `ExoPlayer` instances across the chat list.
   - Prevents `MediaCodec.CodecException` and OOM audio collisions.
   - Automatically pauses background players when a new media card starts playing.
2. **`ArtifactChatCard.kt` & Chat Integration**:
   - Location: `app/src/main/java/com/example/ui/chat/ArtifactChatCard.kt`
   - Renders inline in `ChatScreen.kt` message bubbles whenever a tool generates a file, attachment, or code block:
     - **Audio Card**: Waveform scrub-bar, duration (`0:15 / 1:30`), play/pause button.
     - **Video Card**: Aspect-ratio thumbnail with play overlay.
     - **Document/Code Card**: Icon badge (PDF, PPT, Code, 3D, HTML), title, file size, and "Inspect" button.
3. **Telemetry**:
   - Log all card impressions and playback events to `LogKeeper.log("ExoPlayerPool", "Play", ...)`.

---

## Mini-Phase 2: Polymorphic Bottom Sheet & Standalone Multitasking (Completed)
**Target**: Tier-2 full inspection dialog and independent Android app multitasking.

### Deliverables:
1. **`UniversalArtifactSheet.kt`**:
   - Location: `app/src/main/java/com/example/ui/chat/UniversalArtifactSheet.kt`
   - Evolve `PWAPreviewBottomSheet.kt` into a polymorphic viewer supporting:
     - `WEB_APP`: Sandboxed WebView with live preview vs. code syntax toggle.
     - `SLIDES`: Swipeable presentation deck view.
     - `DOCUMENT`: Native PDF renderer page scroll.
     - `MEDIA`: High-fidelity audio/video player with waveform.
     - `MODEL_3D`: Lightweight Three.js / `<model-viewer>` orbit preview.
   - Standardized Top Bar:
     `[Title] [Preview 🌐 | Code 💻] [💾 Save to Artifacts] [✏️ Edit in Workspace ▾] [✕ Close]`
2. **`StandaloneArtifactActivity.kt`**:
   - Location: `app/src/main/java/com/example/ui/artifacts/StandaloneArtifactActivity.kt`
   - Declared in `AndroidManifest.xml` with `documentLaunchMode="always"` and `FLAG_ACTIVITY_NEW_TASK`.
   - Allows web mini-apps to run in their own Android Recent Apps task card, independent of the main chat process.
3. **Backup & Log Keeper**:
   - Log sheet transitions to `LogKeeper`.
   - Verify saved standalone artifacts continue to be captured cleanly by `BackupManager.kt`.

---

## Mini-Phase 3: Native Zero-Cost Silicon Engines & Workspace Viewers
**Target**: Native Android hardware-accelerated viewers in the Workspace Tab.

### Deliverables:
1. **`ImageViewer.kt`**:
   - Location: `app/src/main/java/com/example/ui/code/ImageViewer.kt`
   - Jetpack Compose Canvas backed by Google Skia GPU primitives.
   - Pinch-to-zoom, pan, rotation, and rectangular bounding box drawing.
2. **`PptViewer.kt` Expansion**:
   - Location: `app/src/main/java/com/example/ui/code/PptViewer.kt`
   - Smooth `HorizontalPager` slide viewing, bullet-point editing, and slide reordering.
3. **`StageWebViewer.kt` (3D & Maps Shell)**:
   - Location: `app/src/main/java/com/example/ui/code/StageWebViewer.kt`
   - Minimalist `<10KB` webview shell.
   - Injects `<model-viewer>` WASM only when opening `.stl`, `.gltf`, or `.obj`.
   - Injects Leaflet.js with offline OpenStreetMap vector tiles only when opening `.geojson` or `.kml`.
   - Destroys WebGL context immediately on exit (reclaims 60–100MB RAM).
4. **Wire to `CodeScreen.kt` & `FileExplorer.kt`**:
   - Replace placeholder image text in `CodeScreen.kt` with `ImageViewer`.
   - Update `FileExplorer.kt` with "Open With..." context menu supporting all viewer types.
   - **Artifact Workspace Control Bar**: Add a persistent top action bar when any non-code artifact is open:
     - **Mode Switcher**: Segmented toggle `[ 👁️ View ]` $\leftrightarrow$ `[ ✏️ Edit ]` (switches between native viewer rendering and editable source markup/content).
     - **"Pass to AI" (`[ 🤖 Pass to AI ]`)**: Stages the active artifact reference and context into the Chat composer and pivots back to Chat for instant prompting.

---

## Mini-Phase 4: Foldable Accordion Tools Directory & Thread Settings Expansion
**Target**: Clean, zero-bloat tool discovery and encrypted thread-scoped secrets management.

### Deliverables:
1. **`ToolAccordionGroup.kt`**:
   - Location: `app/src/main/java/com/example/ui/settings/ToolAccordionGroup.kt`
   - Collapsible Material 3 accordion cards grouping tools into:
     - 📂 Global Core Utilities (`view_file`, `edit_file`, `list_dir`)
     - 💻 Workspace Scoped Tools (Code AST, diffs)
     - 🎬 Media & Silicon Tools (Video trim, audio synthesis)
     - 🔌 External MCP Servers (GitHub, Cloudflare)
   - Features: Active count badges (`X/Y Active`), animated chevron rotation, and master enable/disable switches.
2. **`ThreadSettingsScreen.kt` Tabs Expansion**:
   - Add **Integrations (🔌)** tab: Client-side presets for GitHub, Cloudflare, Google AI Studio, custom REST webhooks.
   - Add **Secrets (🔑)** tab: Visual manager for `ThreadSecretsStore` with masked input fields (`••••••••••••`) and zero cleartext exposure.
3. **Encrypted Backup & Restore**:
   - Connect thread-scoped secrets and custom integration toggles directly into `BackupManager.exportBackup()` and `restoreBackup()`.

---

## Mini-Phase 5: Hybrid In-Chat Parameter Controls & "Mark-to-Chat" Bridge
**Target**: Two-way interactive loop between Viewers and Chat.

### Deliverables:
1. **Proactive In-Chat M3 Parameter Widgets**:
   - AI can render native interactive widgets in the chat bubble stream: Sliders (e.g. gain, temperature, count), Toggle switches, Segmented buttons, Dropdowns.
   - Interacting with widgets dispatches an immediate structured callback to the AI turn (`{"action": "parameter_change", "key": "volume", "value": 75}`) without typing.
2. **"Mark-to-Chat" Protocol**:
   - Selecting code lines in `TextViewer`, text clauses in `PdfViewer`, or drawing a bounding box in `ImageViewer` surfaces a floating action pill: `[💬 Ask AI about selection]`.
   - Tapping the pill stages the exact file path, line numbers / bounding coordinates, and context snippet directly into the `ChatScreen` text input field for human review.
3. **Full System Verification & Receipts Audit**:
   - Run complete `compile_applet` build.
   - Perform security self-scan (no committed keystores, zero credential leaks).
   - Log full audit record in `/receipts/`.

---

## 3. Implementation Rules & Constraints
1. **Discussion Override**: When user specifies "just discuss", do not write code, edit files, or alter this document.
2. **Credential Immunity**: Never commit API keys, tokens, or keystores. Thread secrets must remain strictly in `ThreadSecretsStore` and hardware/AES keystore.
3. **LogKeeper Standard**: Every new component logs through `LogKeeper.log(component, action, details)`.
4. **Clean Decoupling**: Each Mini-Phase must compile cleanly and pass unit verification without relying on unwritten subsequent phases.
