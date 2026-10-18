# Blueprint: OmniRoute / AI Studio Mobile IDE

## 1. Core Architecture & Infrastructure
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Dual-Server Isolation**: 
  - **AI Proxy Server**: A full Node.js server environment (similar to Termux execution) running OmniRoute strictly in the foreground. It is tied to the app's lifecycle, providing internal AI agents a resilient gateway to LLMs. Full Node.js support is mandatory due to the complexity and security requirements of the proxy.
  - **User App Server**: A separate local web server (NanoHTTPD) used exclusively for previewing the active thread's generated web apps. It spins up on demand via a top-bar "Play" button and shuts down when closed. 
  - *Network Binding*: Both servers bind exclusively to `localhost` to ensure strict isolation and prevent external network exposure unless explicitly authorized.
- **App Lifecycle & Execution**: Chat threads and the embedded proxy run strictly in the foreground. The currently opened chat thread always has execution priority.
- **Agentic Engine**: A multi-agent system (Architect, Coder, Reviewer) for building, debugging, and auditing code. Supports local AI models (via OmniRoute) alongside cloud providers.
- **Orchestration & State Management**: The Android app acts primarily as a UI frontend and orchestrator. It manages file I/O, UI state, and process lifecycles, delegating heavy API routing to OmniRoute and reasoning to the Agentic Engine.
- **Android App as Tool Executor**: The Android shell handles the hard native work for tools and integrations:
  - **Auth & API calls**: Managing OAuth tokens for Google Drive, communicating with the Firebase SDK (Python Sandbox for tools), making GitHub API calls.
  - **Local Execution**: Running the local JS Sandbox.
  - **File Operations**: Natively opening, reading, compressing, and decompressing files, PDFs, and PPTs.
  - **Default MCP Providers**: GitHub, GDrive, and Firebase are available as default MCP connections.
  - **Native Tools**: Features like search are executed natively by the app.
- **Extensible Tool Infrastructure**: The core infrastructure includes a built-in tool system from the start. This allows tools (built-in, Firebase Python, Drive, GitHub MCPs) to be registered at the app level, ready to be utilized by the Agent logic once it is integrated.
  - **Tool Permissions & Security**: Includes explicit user-approval hooks for sensitive tool executions (e.g., file deletion, external network requests) to ensure safety prior to agent automation.
- **Memory/Context**: Modular memory architecture for persistent context retention.
- **Log Keeper**: An always-on, headless logging system built from the start. Accessible via a Floating Action Button (FAB) anchored to the bottom-left corner of the screen. Operates as a zero-heap headless catcher that streams sanitized logs directly to a 2MB rolling disk file (`omniroot_active_logs.jsonl` / `.old`). UI operates as an on-demand reader without keeping full logs in RAM. Bounded in-memory parachute buffer (50 items) is retained strictly for emergency crash drops in Downloads. Strictly filters out ALL passwords or credentials. Includes a master on/off switch and time filters, with one-tap export to Downloads.
- **Encrypted Backup & Restore**: Located in Global Settings. Exports and imports all chats, API keys, workspace configurations, chat settings, and tool permissions with authenticated AES-256-GCM encryption and PBKDF2 key derivation (10,000 iterations).
- **Multi-Account AI Key Management & Intelligent Auto-Pooling**: Supports storing multiple keys and accounts per provider (e.g. Personal vs Work) with dedicated Google Account Chooser flow (`https://accounts.google.com/AccountChooser?continue=...`), quick alias tagging, and one-tap active key switching in the AI Manager. Supports per-model account binding (e.g. `google_ai_studio/gemini-2.5-pro@Work` -> `google_ai_studio/gemini-2.5-flash@Personal`) and automatic account quota pooling that catches HTTP 429 rate limits and cycles through all available account keys before falling back to lower-tier models.
- **Verified Zero-Card Free-Tier Providers & Preset Routing**: Clean, verified zero-card cloud inference providers integrated into the native routing engine and AI Manager: Cerebras (1M tokens/day wafer-scale), Mistral (Experiment tier / Codestral), SambaNova (200k tokens/day SN40L enterprise chips), GLHF AI (Llama 3.3 70B & Qwen 2.5 72B free developer beta), Hugging Face Serverless, and Cohere (Trial tier), alongside Google AI Studio, Groq, and OpenRouter free catalog. Includes one-tap preset combo routing in the Translator Tab ("Zero-Cost Ultra Speed" and "Zero-Cost Frontier Coding") that automatically failover across free models and multiple accounts on rate limit exhaustion.

## 2. Global UI Layout & Navigation
- **Global Sidebar (Left Drawer)**: Contains the following list of items:
  1. **New Chat**: Top action to create a new thread.
  2. **Artifacts**: Dedicated page for saved artifacts from chats. All chat artifacts are temporary unless saved here. Ultimately all artifacts data will be remembered.
  3. **Design**: Placeholder for now (to be added after backend).
  4. **List of Chats (Repos)**: The multi-thread workspaces list.
  5. **Global Settings**: At the bottom.
- **Global App Settings (The "Library")**: Accessed via the sidebar. Structured as a simple list of items, where tapping each item opens its own dedicated page:
  - **Skills**: Add/edit/delete specific agent capabilities.
  - **Tools & Plugins**: Manage standard tools and Plugins (a hybrid of skills, tools, and MCP).
  - **Memory & Artifacts**: Global memory configuration and saved artifacts.
  - **System Instructions**: Global guardrails and rules.
  - **Built Agents**: Library of custom-configured agents.
  - **Model Context Protocol (MCP)**: Configuration for MCP integrations (GitHub and Firebase Python included as default providers; Cloudflare MCP to be checked later).
  - **Google Drive Archive**: Configure Google Drive integration. Old chats and workspaces (repos older than 10) are automatically compressed into ZIP files and saved in a designated Google Drive folder.
- **Fixed Bottom Navigation**: A pill-shaped bar fixed at the absolute bottom of the screen (not floating). It switches between **Chat** and **Code** views, accompanied by a 3-dot menu for workspace actions. The 3-dot menu contains the following options:
  - **Thread Settings**: Access the active thread's configurations.
  - **Export**: Export the workspace as a ZIP file or quick push to GitHub.
  - **Remix**: Copy the repository and all integrations (agents, skills, settings) into a new workspace, clearing chat history and the GitHub repo link.
  - **AI Manager Token Panel**: Access the integrated proxy web interface.
- **AI Manager Token Panel**: An integrated WebView pointing to the embedded proxy's local web interface to manage API keys, routing, analytics, and connections to **local AI models** (e.g., Ollama, on-device models, LM Studio). OmniRoute natively handles on-device AI inference and routing.

## 3. Workspace Views (Thread Specific)
### A. Chat View (Google AI Studio Style)
- **Top Bar (Agent Switcher)**: Tap the active Agent to open an Agent Card and customize:
  - **Model Order**: Fallback chains (e.g., try Claude, then GPT-4).
  - **System Prompts**: Specific rules for the active agent.
  - **Schedules/Reactions**: Post-task automated actions (e.g., auto-lint after coding).
- **Chat Input**: A scrollable (not infinitely expandable) input box to ensure the bottom text is always visible. It features a "+" button for uploading attachments (`.txt`, `.zip`, `.pdf`, code files), and an **Agent/Model Selector Pill** built directly into the input bar to quickly switch agents or models before sending.
- **Chat Interface & History**:
  - Beneath the user's sent message, a detailed list of actions and files modified, added, or deleted is displayed.
  - **File Bottom Sheet**: Tapping any changed file in the chat history opens it in a bottom sheet view for quick inspection (similar to the Artifacts UI).
  - **Native File Revert**: The app's native file system records a history of file changes independently of the AI. After an AI reply, the UI provides a file diff view and a button to revert the workspace back to that exact state using the local app-level file history.
- **Thread Settings Page**: A dedicated settings page for the active thread, styled with horizontal pill-shaped tabs at the top (like Google AI Studio):
  1. **Universal**: Thread-level tools, skills, MCPs, plugins, and guardrails accessible to all agents.
  2. **Agents**: Manage the list of active agents in the thread.
  3. **Versions**: Access version history/snapshots for the workspace.
  4. **Secrets**: Thread-specific API keys and secrets.
  5. **GitHub**: Configure GitHub integration specific to this thread for pushing to repositories.

### B. Code View (Acode Style)
- **File Tree (Right / End Drawer)**: Slide-out drawer on the right side showing repository structure, avoiding collision with the global left sidebar. Includes a search bar. The project root, folders, and files all have their own 3-dot context menus for file operations (create, delete, rename). Tapping a file opens it in the main editor *without* closing the drawer.
- **Main Editor**: A lightweight, standard text editor acting as the main view for inspecting or tweaking code.
  - **Live Generation View**: When an agent is writing code, the editor switches to a read-only live view (or displays a structured loading state) so the user can watch the code being generated in real-time or understand that a process is running.

## 4. Agent Capabilities & Export
- **Parallel Agent Execution (Antigravity Architecture)**: The IDE supports a parallel agentic model where a master orchestrator spawns and coordinates sub-agents to perform tasks concurrently (e.g., searching docs, generating code, running lint tests). This allows non-blocking background operations, streaming progress back to the main UI.
- **File Readers & Analysis**: Built-in tools for agents to process user uploads.
- **Code Sandboxing**: Secure execution environments for tools and scripts:
  - **On-Device JS Sandbox**: For local, lightweight JavaScript execution.
  - **Cloud Firebase Python Sandbox**: [ON HOLD] Temporary, secure Python environment for heavy tool execution.
- **Artifacts**: Support for generating standalone small web apps. Artifacts have the capability to embed AI logic directly within them.
  - **Design Studio (UI Map Artifact)**: A specialized visual artifact accessible from the main sidebar. Allows users to create clickable UI interface maps (e.g., "tap note card -> settings screen"). These preview web apps are highly customizable (adjustable button sizes, fonts, colors). The tool divides designs into screens/menus and exports them as well-structured JSON, code, or images (to prevent AI hallucination) to serve as exact UI blueprints for building native Kotlin shells in chat.
- **Export & Deployment**:
  - **GitHub Integration**: Push directly to remote repositories using a PAT or OAuth App, replicating Google AI Studio's export functionality.
  - **ZIP Export**: Package the local workspace into a `.zip` for manual extraction.

## 6. CI/CD & Build Pipeline
- **GitHub Actions Integration**: The Android APK is strictly built using a GitHub Actions workflow upon pushing to `main` or manual dispatch.
- **Workflow configuration**: Uses `ubuntu-latest`, JDK 21, and Gradle setup. It automatically generates a transient debug keystore (`keytool -genkey`) during the workflow to bypass local keystore credential risks and builds the APK using `gradle assembleDebug --no-daemon --no-configuration-cache`.

## 5. Development Phases
- **Phase 1 (Completed)**: Setup project foundation, UI skeleton (Sidebar, Fixed Bottom Navigation, Dual-tab layout), Local File System tracking, and the shell-level **Log Keeper**.
- **Phase 2 (Completed)**: Implement the NanoHTTPD preview server and the Extensible Tool Infrastructure (building the hooks and empty spaces for tools, skills, and MCPs).
- **Phase 3 (Completed)**: Replace Chat placeholders with real state (dynamic action history, message list, model picker, file attachment bottom sheet). Implement the **Agent Card (Top Bar)** UI and the **Chat Input UI** (scrollable input box with embedded Agent/Model Selector Pill). Implement the real **Global Sidebar UI** (New Chat, Artifacts, Design, List of Chats, Global Settings). Implement the 3-dot menu actions UI in the Fixed Bottom Navigation (Thread Settings, Export, Remix, AI Manager Token Panel). Update Chat top-right "Play" button to open an artifacts list placeholder.
- **Phase 4 (File System & Native Readers)**: Build the real File Explorer wired to the local Android file system. Implement Native File Readers & Operations (app-level handling for PDF, PPT, and ZIP compress/decompress).
- **Phase 5 (Code Editor)**: Implement the Code Editor (Acode Style). Connect it to the File Explorer to open, edit, and save real files. Include live generation view states and native file revert UI. Add tabs to the File Explorer drawer (File Tree, Issues, Pull Requests). Add local Issue and PR creation to the file/folder 3-dot context menu.
- **Phase 6 (Global Settings UI & App Expansion)**:
  - **Global Settings List**: Build the Global App Settings as a list of items routing to dedicated pages:
    - **Agents**: List of built agents (and ability to build new ones).
    - **Skills**: Add/edit/delete specific agent capabilities with support for Functional Skills and Soul Skills (agent persona/constitution guardrails), search filtering, and remote import via direct link/URL.
    - **Tools**: List of tools with a permission toggle beside each name (Always Ask, Use Freely, No Permission) and integrated search bar.
    - **MCP**: List added custom MCPs with search bar, account alias tagging, and quick templates. Include a FAB to add new ones. Built-in MCPs (GDrive, GitHub, Firebase, Cloudflare, Kaggle) are present for the user to connect.
    - **Plugins**: User-made combinations of skills, tools, MCP servers, prompt instructions, and strict operational restrictions (guardrails) into modular, importable plugins. Includes multi-selector builder and remote manifest import.
    - **Integrations**: GitHub, Firebase, and GDrive more settings. These three are greyed out if not connected. Inside are extra required settings.
    - **General**: System performance, max concurrent WebViews selector (1-4, default 2) with low-memory warning for <=3GB RAM devices, artifact generation timeout (30-180s, default 90s), auto-reload on crash, auto-sync token states, and cache purge.
    - **Code Editor**: Fine-tune font scaling (slider 10-24sp), tab spacing (2, 4, 8 spaces), line numbers, word wrap, auto-indent, and bracket auto-close with interactive live code preview.
    - **Artifacts (Preview)**: Settings for artifacts preview.
    - **Library Management**: Component Library settings gated by Google Drive connection (shows clean empty state with 'Connect Google Drive' CTA when disconnected; shows synced folders and prompt assets when connected).
    - **Permissions**: System permissions monitor (Microphone, Notifications, Storage/Media) with live grant status, in-app permission launchers, and direct link to Android System App Settings.
    - **Font & Typography**: App-wide typeface selector (System Default, Sans-Serif, Serif, Monospace), font scaling slider (0.85x to 1.30x), and live typography preview card.
    - **Encrypted Backup & Restore**: Full AES-256-GCM encrypted backup/restore flow with PBKDF2 key derivation for chats, API keys, and workspace configurations via Android SAF document picker.
    - **AI Manager Panel**: A professional, tabbed dashboard (Directory, Active Keys, Available Models, Token Counter, Model Rater) inspired by OmniRoute.
  - **Global Sidebar Addition (Library)**: Add a new element called "Library" to the Global Sidebar where all uploaded files are stored (synced to GDrive, similar to modern AI services).
  - **Chat Token Bar**: Add a small bar in the Chat view to monitor if token limits are exceeded.
- **Phase 7 (Thread Settings & Export UI)**:
  - **Thread Settings UI**: Build the dedicated Thread Settings page with Google AI Studio-style pill-shaped tabs (Universal, Agents, Versions, Secrets).
  - **Export Flow**: Wire up the 3-dot menu "Export" action to provide two distinct options: ZIP or GitHub.
  - **GitHub Export Bottom Sheet**: Build a focused bottom sheet (without pill tabs) specifically for committing changes. It must display the target repository/branch, a commit message text area, a list of explicitly changed files with their status (Modified, Added, Deleted), and a "Stage and commit all changes" button. This performs a targeted batch push of only the modified files, rather than a full zip upload.
- **Phase 8 (Artifacts & Previews)**: Implement Artifacts generation and management. Implement the **Universal Artifact Sheet** and **PWA Bottom Sheet Preview** to load PWAs and Artifacts from the Chat top-right list and inline message cards.
  - **Modular Workspace & Artifact Lifecycle (Completed - Mini-Phases 1 & 2)**:
    - *Mini-Phase 1 (In-Chat Non-Text Artifact Cards & Media Pool)*: Created `ExoPlayerPool` for lifecycle-safe recycled playback, `ArtifactChatCard` for 8+ non-text media types, and `ArtifactExtractor` parsing tool outputs and file histories.
    - *Mini-Phase 2 (Polymorphic Sheet & Standalone Multitasking)*: Created `UniversalArtifactSheet` with View/Code segmented toggle and enterprise secrets injection, declared `StandaloneArtifactActivity` with `documentLaunchMode="always"` for isolated Android Recent Apps multitasking cards, added standalone pop-out actions to `ArtifactsScreen`, and wired artifact persistence into encrypted `BackupManager.kt`.
  - **2-Part Artifact Secrets System (Completed - Mini-Phases 1-5)**: Full implementation of the Decoupled 2-Part Architecture (`TWO_PART_ARTIFACT_SECRETS_PLAN.md`):
    - *Part 1 (The Repo)*: Code stored in `/artifacts/<id>/repo/` and `/workspaces/<ws_id>/` strictly containing client-side HTML, CSS, JS, and clean assets.
    - *Part 2 (Encrypted Secrets Bundle)*: Stored in `/artifacts/<id>/secrets_bundle.enc` encrypted via AES-256-GCM (`ArtifactKeyStore`).
    - *Runtime In-Memory Secrets Injection*: Injected directly into the WebView (`PWAPreviewBottomSheet` and `OpenedArtifactViewerDialog`) via `evaluateJavascript` as `window.__SECRETS__ = Object.freeze(...)` with a `'secretsready'` CustomEvent. Part 2 files are strictly withheld from workspace directories, preventing AI agent tool leaks.
    - *Blind Fork & Edit*: Preserves thread-scoped secrets across workspace forks without exposing raw values to the AI workspace filesystem.
    - *Hardened Export Filter*: `HardenedExportManager` strips `*.enc`, `.env*`, and credentials on GitHub/GDrive/ZIP export, automatically generating a blank `secrets.example.json` template with verified 0-secret exposure.
- **Phase 9 (Native AI Manager & Router)**: Implement a lightweight, pure-Kotlin local AI Gateway inspired by OmniRoute. Features a comprehensive provider directory (API-based, Free tiers, Local), advanced analytics (total request counts, token usage, and total cost estimation), dynamic routing strategies (priority lists, combo routing for cost/speed/quality optimization), a stable guided WebView for API key generation (Direct-to-Key), a Foreground Service proxy, and native on-device execution of `.gguf` models (via `llama.cpp` integration). Optimized for 3GB RAM Android Go. (See `PHASE_9_OMNIROUTE.md` for detailed sub-phases).
  - **Omnivian Artifact Provider System (Completed)**: Headless Claude.ai public artifact inference subsystem. Features `ArtifactProviderEntity` and `ArtifactProviderDao` (Room v21 migration) where `sessionTokens` is a local cache only and the live artifact is the source of truth (resynced via `getState()` on startup and every 30s). Uses `ArtifactKeyStore` (hardware/TEE-backed AES-256 AndroidKeyStore) to encrypt and store original passkeys for `unlock()` and `requestPasskey()`, while Room stores the SHA-256 hash for artifact verification. Features `ArtifactBridge` (`@JavascriptInterface` with MainLooper thread marshaling and streaming chunks), `ArtifactWebView` (headless Chromium instance with postMessage relay and exponential crash backoff), `ArtifactProviderPool` (LRU concurrency manager with user-controlled cap), `ArtifactRouter` (delegated from `FallbackChainRouter` for zero-cost routing), and an "Artifacts" tab in the AI Manager with real-time status dots, live token bars, reload, and passkey configuration. Controlled via the new "General" screen in Global Settings.
- **Phase 10 (Completed - Tool Calling Engine, Multi-Sandboxes, Failover Routing, Document Parsers & MCP Ecosystem)**: Full native implementation of the agentic tool calling engine, multi-sandboxes (JS V8, Shell Process, PRoot Linux), document parsers (PDF, DOCX/PPTX, Zip/Unzip), MCP client (JSON-RPC 2.0 / SSE / HTTP) with Multi-Account MCP support (e.g. GitHub Personal vs Work with isolated alias namespacing in tool definitions and SQLite DB v19 migration), quick-add presets for Google Drive, Cloudflare, GitHub, and Kaggle, foldable and folded-by-default Global Settings Tools page with distinct App-Provided vs MCP-Provided sections, fallback chain router, tool permissions, and agentic multi-turn executor integrated into chat with interactive tool call UI bubbles. (See `PHASE_10_TOOLS_AND_SANDBOXES.md` for complete specification).
- **Phase 11 (The Brain, Memory & Agent Ecosystem)**: Implement core Agent Logic and Diff Parsing. Connect them into the pre-built UI and tool infrastructure. Implement the Modular Memory Architecture for persistent context retention. Implement a multi-agent PR/Issue flow (Planner, Coder, Reviewer) controlled by optional thread/agent settings that utilizes local Issues/PRs like a GitHub MCP workflow to optimize token usage. Includes Phase 11.4 (Surgical Edit Tool): building a robust "Search & Replace" Block editor (`edit_file` tool) requiring `target_text` and `replacement_text` to enforce fast, token-efficient, and line-shift-safe local AI code modifications.
  - **Phase 11.5 (Conversational In-Chat Agent Builder)**: Describe a desired agent in chat to have the AI compile a structured Agent Manifest rendered as an **Interactive Agent Review Card** in the chat timeline (editable Name, Icon, Tag, System Instructions, Model, and Tool bindings) with a 1-tap "Save to Agent Library" action that persists to the Room DB with custom tags.
  - **Phase 11.6 (Complex Executable Agents & Tool/Sandbox Binding)**: Support Hermes/Claude/OpenAI-style complex executable agents that go beyond static prompt instructions by binding Phase 10 Sandboxes (PRoot Linux, JS, Shell) and MCP tools, with structured function calling schemas (`<tool_call>`/JSON) and multi-step execution loops.
- **Phase 12 (Completed - Antigravity Orchestration & Multi-Agent PR/Issue Flow)**: Full implementation of the Parallel Agent Execution (Antigravity Engine) orchestrator (`AntigravityOrchestrator.kt`), mobile concurrency gating via `Semaphore(permits = 2)`, and specialized sub-agent roles:
  - *Architect Agent (`ArchitectAgent.kt`)*: Automatically decomposes complex user objectives into atomic `WorkspaceIssueEntity` entries in Room DB with acceptance criteria.
  - *Coder Agent (`CoderAgent.kt`)*: Analyzes workspace files and generates surgical unified diffs stashed into `WorkspacePullRequestEntity` in Room DB.
  - *Reviewer Agent (`ReviewerAgent.kt`)*: Adversarial quality and security auditor scanning for credential leaks (`.keystore`, `.env`, tokens), syntax verification via `UnifiedDiffParser`, and automated acceptance scoring.
  - *Human-in-the-Loop PR Management (`WorkspacePrManager.kt` & `PullRequestReviewDialog.kt`)*: 1-tap "Approve & Merge Diff" applying changes atomically via `PatchApplier` and updating Room issue statuses.
  - *Antigravity Chat Integration (`AntigravityTimelineCard.kt`)*: Real-time pulsing status card embedded in `ChatScreen` with sub-agent role tracking, live progress messages, cancellation controls, and `/swarm` / `/antigravity` command dispatching. Zero credential exposure guaranteed.
- **Phase 13 (Completed - Storage Security, Hardware KeyStore, Cloud Archiving & CI/CD)**: Enterprise storage security and cloud export hardening:
  - *Hardware-Backed KeyStore (`DatabaseKeyStore.kt`)*: Master AES-256 key derivation via AndroidKeyStore inside hardware TEE / StrongBox with cryptographically secure 256-bit passphrase encryption and in-memory wipe patterns (`Arrays.fill(bytes, 0)`). Zero credentials on disk.
  - *Storage Security & Integrity Manager (`DatabaseSecurityManager.kt` & `DatabaseSecurityDialog.kt`)*: Real-time non-destructive database audits checking file size, table counts across all 19 Room entities, hardware security tier, and `PRAGMA integrity_check` verification.
  - *Hardened Cloud & Drive Workspace Archiver (`WorkspaceArchiveManager.kt`)*: Single-tap creation of sealed `.omniroot.zip` archive packages ready for Google Drive or external cloud backups. Enforces zero-secret exposure via `HardenedExportManager.isSensitiveFile`, injects root `manifest.json` with SHA-256 checksums, and protects restorations against Zip Slip path traversal.
- **Phase 14 (Completed - Context Drift Mitigation & Intelligent Memory Engine)**: Enterprise-grade context drift prevention across Cloud APIs and Local GGUF models:
  - *Rolling Conversation Summarizer (`RollingSummaryManager.kt`)*: Ongoing compression of pruned historical dialogue turns into a structured digest (`## Conversation History Summary`) permanently included in system prompt assembly to maintain multi-turn coherence.
  - *Automated Background Fact & Memory Promotion (`FactExtractorEngine.kt`)*: Asynchronous regex/heuristic engine extracting architectural decisions, project rules, and user preferences at turn completion, auto-promoting them into Room-backed `MemoryRepository` with critical invariants pinned.
  - *Dynamic Model-Specific Token Budgeting (`ContextBudgetManager.kt`)*: Model profiling reserving 20% generation headroom, dynamically allocating budgets for System/Rules, Rolling Summary, and Active Messages for both Cloud (Gemini 1M, Claude 200k, GPT-4o 128k, DeepSeek 64k) and Local GGUF (2k/4k) models.
  - *Unified Local GGUF Execution (`ChatScreen.kt`)*: Routes local on-device models through `PromptAssembler` with ChatML `<|im_start|>` formatting, preventing context overflow exceptions and memory amnesia.
  - *Context & Token Health Visualizer (`AiTokenPanelBottomSheet.kt` & `ChatScreen.kt`)*: Segmented context allocation meters, rolling summary inspector/purger, and 1-tap pinned memory managers in Compose UI.
- **Phase 15 (Design Studio)**: Build the Design Studio / UI Map Artifact tool for generating UI reference blueprints.
- **Phase 16 (Completed - Google AI Studio Comment & Artifact Integration for Workspace)**:
  - *Contextual Staging Bridge (`WorkspaceChatBridge.kt`)*: Upgraded `StagedReference` with first-class `userComment: String?` property across all subtypes (`CodeLines`, `ImageRegion`, `MediaTimestamp`, `DocumentPage`, `PresentationSlide`, `GenericArtifact`) and structured markdown prompt assembly.
  - *Contextual Comment Bottom Sheet (`ArtifactCommentBottomSheet.kt`)*: Dedicated M3 sheet with 7 quick action presets ("Refactor", "Explain code", "Find bugs & edge cases", "Add unit tests", "Optimize performance", "Add documentation", "Check security"), custom directive text input, and direct staging into the AI context basket.
  - *Multimodal Editor Comment Triggers*: Integrated floating comment buttons into `TextViewer.kt` (selection-aware line range comments), `ImageViewer.kt` (image annotations), `AudioViewer.kt` (timestamp-anchored audio comments), `VideoViewer.kt` (frame timestamp comments), `PdfViewer.kt` (page-anchored comments), and `FileExplorer.kt` (quick file comment modal).
- **Phase 17 (Completed - Google AI Studio Build UI Polish: Chat & Code Tabs)**:
  - *Navigation & Pill Switcher (`FixedBottomNav.kt`)*: Updated tab label from "Workspace" to "Code" matching Google AI Studio Build UI ("Chat | Code"), added `ChatBubble` and `Code` vector icons, refined pill container border stroke (`outlineVariant`), and animated surface selection state.
  - *Code Breadcrumbs & IDE Header (`CodeScreen.kt`)*: Replaced basic title text with a real-time breadcrumb path (`workspace / ... / filename`), live generation view indicator, and compact action icon grouping.
  - *Unified Viewer Sub-Toolbar (`CodeScreen.kt`)*: Polished the control bar into a sleek toolstrip with refined Source badge, "Stage" to context basket button, contextual comment button, and window controls.
  - *IDE-Style File Tabs Strip (`CodeScreen.kt`)*: Transitioned bottom tab carousel to modern IDE-style tabs with active underline accent, file-type color tints, close tab button, and browse files trigger.
  - *File Explorer Tree Polish (`FileExplorer.kt`)*: Polished search bar with M3 surface tokens and rounded shape, added expand/collapse chevron arrows to directory nodes, alignment spacers for files, and color-coded file icons matching active tabs and IDE standards.
- **Phase 18 (Completed - Native Folded Web AI Tabs in Global Sidebar & Custom Tabs Strip Removal)**:
  - *Custom Tabs Cleanup (`WebAiCustomTabManager.kt`)*: Removed broken and OEM-flaky `setSecondaryToolbarViews` RemoteViews bottom strip and bitmap generation. Custom Tabs now open cleanly, full-screen, and lightning-fast across Firefox, Chrome, and all Android browsers.
  - *Zero-Overhead Persistence (`WebAiCustomTabManager.kt`)*: Implemented on-demand `SharedPreferences` persistence for open Web AI tabs (`persistTabs` / `restoreTabs`) with defensive initialization and zero background tasks or memory footprint when the app is idle or reopened.
  - *Sidebar Folded Accordion (`GlobalSidebar.kt`)*: Pinned at the very top above repository list, defaulted to folded state. Displays small numeric badge for open tabs count. Single tap directly opens `WebAiPortalScreen` when 0 tabs are active. When active tabs exist, tapping unfolds the tab strip smoothly, pushing items below down.
  - *Native Tab Navigation & Actions (`GlobalSidebar.kt` & `WebAiCustomTabManager.kt`)*: Header includes "Close All" (✕) button and "+" shortcut. Each active tab item displays branded color initial pill, service name, active indicator, tap-to-switch direct launch in Custom Tabs, and an individual cross (✕) button beside the name to close that tab.
  - *Multi-Tab Session Merging & Seamless Switching*: `launchSession` merges and preserves previously open tabs when adding new providers rather than overwriting. `switchToTab` uses unified `CustomTabsIntent` with automatic fallback to keep browsing inside Custom Tabs. Broadcast receiver updates persist to storage immediately with context.
- **Phase 19 (Completed - Runtime Resilience, Low-Memory Safety & Two-Way Code Synchronization)**:
  - *RAM Protection & JNI Model Suspension (`WebAiCustomTabManager.kt` & `HeavyTaskThrottler.kt`)*: Automatic invocation of `HeavyTaskThrottler.suspendHeavyTasks(context)` upon any Custom Tab launch or tab switch. Suspends on-device GGUF / llama.cpp inference engine, unloads media playback in ExoPlayerPool, and triggers Android memory trimming to provide maximum free heap headroom and eliminate Low Memory Killer (LMK) process termination.
  - *Two-Way Code-to-Chat Synchronization Bridge (`WorkspaceChatBridge.kt`, `CodeEditorState.kt`, `FileHistoryEngine.kt`, `PromptAssembler.kt`)*: Added `WorkspaceFileEditEvent` tracking whenever a user manually edits or reverts files in the Code tab. `PromptAssembler` injects recent manual revision timestamps and filenames into the dynamic system prompt, eliminating stale diff baselines and patch rejections.
  - *Semantic Outline Extractor for Context-Constrained Local Models (`WorkspaceChatBridge.kt`)*: Built `extractSemanticOutline` to automatically condense large staged code files (>60 lines) into structural signatures, class/interface declarations, and comments while omitting bulky internal implementation bodies, reducing token consumption by up to 85% to preserve context limits for local GGUF models.
- **Phase 20 (Completed - Single-Instance Web AI Bookmarks, Clean Custom Tabs & Multi-Browser Profile Routing)**:
  - *Full Custom Tabs Strip Purge (`WebAiCustomTabManager.kt`, `AndroidManifest.xml`)*: Completely deleted the legacy RemoteViews bottom strip (`custom_tab_bottom_strip.xml`) and `WebAiTabBroadcastReceiver.kt`. Custom Tabs are now 100% clean, native, and full-screen with zero custom overlay bars, resolving OEM rendering glitches and full-screen takeover issues.
  - *Single Active Session Architecture (`WebAiCustomTabManager.kt`)*: Replaced multi-tab batch accumulation with single-instance Web AI bookmarks. Only one Web AI is opened at a time. Launching an AI suspends on-device heavy tasks (`HeavyTaskThrottler`), applies target browser package and ephemeral flags, and opens directly.
  - *Multi-Account Multi-Browser Routing (`WebAiProviderManager.kt` & `WebAiCustomTabManager.kt`)*: Solved the Android single-cookie-jar limitation by supporting per-profile target browser package assignment (e.g. Personal account in Firefox, Work account in Chrome) as well as Ephemeral/Incognito mode, enabling true multi-account isolation without session collisions.
  - *Sidebar Folded Bookmarks Hub (`GlobalSidebar.kt`)*: Retained the pinned accordion at the top of the Global Sidebar, transformed into an AI Bookmarks manager. Folded by default with bookmark count badge; unfolds to list configured AI services with brand initials, profile labels, target browser indicators, and 1-tap single-session launch.
  - *Portal Screen Simplification (`WebAiPortalScreen.kt`)*: Purged multi-select batch checkboxes, multi-tab launch dialogs, and preset batch buttons. Transformed into an intuitive Bookmark & Profile management portal with direct 1-tap launch and in-place account switching.
- **Phase 21 (Completed - Low-RAM Deep Hibernation & UI Evacuation on Custom Tab Launch)**:
  - *UI Evacuation Before Intent Launch (`WebAiPortalScreen.kt`)*: Triggers `onNavigateBack()` immediately when launching an AI bookmark or resuming a session. Pops the portal composable and unmounts all grid layouts, profile pickers, and animation states, reducing Omnivian to its lightweight root home screen so zero secondary UI trees reside in memory while the browser is running.
  - *Coil Image & Bitmap Memory Purge (`HeavyTaskThrottler.kt`)*: Invokes `Coil.imageLoader(context).memoryCache?.clear()` to immediately dump decoded ARGB_8888 bitmap memory pools upon browser launch, reclaiming 20MB–60MB of RAM.
  - *Headless WebView & Engine Freezing (`HeavyTaskThrottler.kt`)*: Calls `ArtifactProviderPool.pauseAll()` to suspend background JavaScript and DOM execution in headless artifact WebViews, halts voice recognition/TTS in `VoiceManager`, releases on-device GGUF / llama.cpp models, and releases ExoPlayer media sessions.
  - *System-Level Trimming Escalation (`HeavyTaskThrottler.kt`)*: Upgraded memory trimming to `ComponentCallbacks2.TRIM_MEMORY_COMPLETE` across application and activity contexts and runs `System.gc()` and `Runtime.getRuntime().gc()` for immediate heap compaction, preserving a tiny dormant footprint (~20-30MB) that survives low-end phone (2GB-3GB RAM) multitasking without LMK termination or home-screen ejection.


