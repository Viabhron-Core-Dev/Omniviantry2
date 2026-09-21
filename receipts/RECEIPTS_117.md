* 2026-08-12T13:13:00-07:00
* Request: Shrink the Chat input box model picker pill to show only initials (e.g. "O / G") so it doesn't push the Send button off the screen with long model IDs.
* Touched: app/src/main/java/com/example/ui/chat/ChatScreen.kt
* Action:
  * Modified the `Agent/Model Selector Pill` row in `ChatScreen.kt` to extract the initials of the `providerId` and `modelId` (e.g., `openai/gpt-4o` -> `O / G`).
  * Assigned the initials to a small, fixed-size pill string that renders in place of the full raw string.
  * Preserved the `DropdownMenu` so the user can still read the full model names when the pill is tapped.
* Verification: Built successfully.

* 2026-08-26T14:52:00-07:00
* Request: Implement Mini-Phase 10.5 (Model Context Protocol (MCP) Client Architecture & Dynamic Tools).
* Touched: 
  * app/src/main/java/com/example/engine/mcp/McpModels.kt
  * app/src/main/java/com/example/engine/mcp/McpTransport.kt
  * app/src/main/java/com/example/engine/mcp/McpClient.kt
  * app/src/main/java/com/example/engine/mcp/McpDynamicToolAdapter.kt
  * app/src/main/java/com/example/engine/mcp/McpServerManager.kt
  * app/src/main/java/com/example/engine/tools/McpManageTool.kt
  * app/src/main/java/com/example/engine/EngineRegistry.kt
  * app/src/main/java/com/example/engine/db/McpServerEntity.kt
  * app/src/main/java/com/example/engine/db/McpServerDao.kt
  * app/src/main/java/com/example/engine/db/AppDatabase.kt
  * app/src/main/java/com/example/ui/settings/SettingsPlaceholders.kt
* Action:
  * Implemented pure JSON-RPC 2.0 protocol layer with Server-Sent Events (SSE) streaming transport and HTTP POST endpoints.
  * Implemented `McpClient` with `initialize` negotiation (protocol 2024-11-05), `tools/list`, `tools/call`, and `resources/read`.
  * Built `McpDynamicToolAdapter` to automatically register discovered MCP server tools into `EngineRegistry` with schema mapping for AI agents.
  * Built `McpServerManager` with StateFlow registries, auto-connect lifecycle, dynamic tool syncing, and ping latency metrics.
  * Added `McpManageTool` (`mcp_manage`) for LLM-driven MCP inspection.
  * Added Room database persistence with `McpServerEntity`, `McpServerDao`, and `MIGRATION_15_16`.
  * Built interactive MCP settings UI in `SettingsPlaceholders.kt` to view, add, connect, disconnect, and ping MCP servers.
* Verification: Verified via `compile_applet` (clean Gradle build).
* Deviations: None.
* Follow-up: Phase 10 tool enhancements ready for Phase 11 Brain integration.

* 2026-08-30T15:35:00-07:00
* Request: Implement Mini-Phase 11.1 (Context Compressor & Token Budget Engine).
* Touched:
  * app/src/main/java/com/example/engine/brain/ContextBudgetManager.kt
  * app/src/main/java/com/example/engine/brain/SlidingWindowPruner.kt
  * app/src/main/java/com/example/engine/brain/SnippetCompactor.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
* Action:
  * Implemented `ContextBudgetManager` with exact model context profiles (Gemini 1M/2M, Claude 200k, GPT-4o 128k, DeepSeek 64k, Local GGUF 4k), token estimation heuristics, and automatic context headroom safety thresholds.
  * Implemented `SlidingWindowPruner` to preserve core system instructions, active tool chains, and recent conversational turns while truncating stale history under token pressure.
  * Implemented `SnippetCompactor` to cleanly omit middle sections of oversized code blocks, JSON payloads, and tool outputs with transparent line count markers.
  * Integrated `ContextBudgetManager.prepareContext()` directly into `FallbackChainRouter` provider calls to proactively prevent 400 Context Length Exceeded and 429 overflow failures.
  * Fully integrated with `LogKeeper` for token pressure warnings and pruning statistics.
* Verification: Verified via `compile_applet` (clean Gradle compilation).
* Deviations: None.
* Follow-up: Phase 11 Brain integration ongoing.

* 2026-08-31T15:23:00-07:00
* Request: Implement Mini-Phase 11.2 (Chain-of-Thought & Structured Reasoning Parser).
* Touched:
  * app/src/main/java/com/example/engine/brain/ReasoningParser.kt
  * app/src/main/java/com/example/ui/chat/ThoughtBubble.kt
  * app/src/main/java/com/example/ui/chat/ChatScreen.kt
  * app/src/main/java/com/example/engine/orchestrator/AgenticTurnExecutor.kt
  * app/src/main/java/com/example/engine/db/ChatMessageEntity.kt
  * app/src/main/java/com/example/engine/db/ChatMessageMapper.kt
  * app/src/main/java/com/example/engine/db/AppDatabase.kt
* Action:
  * Created `ReasoningParser` to extract `<thought>`, `<thinking>`, `<antThinking>`, and `<plan>` tags cleanly from raw text responses with unclosed tag edge-case safety.
  * Created `ThoughtBubble` Material 3 Composable with smooth animated expansion/collapsing, thinking elapsed time indicator, and distinct Execution Plan styling.
  * Enhanced `ChatMessage` domain model and `ChatMessageEntity` Room persistence with `thoughtContent`, `planContent`, and `thinkingDurationMs` fields.
  * Added `MIGRATION_17_18` in `AppDatabase` (incrementing database version to 18) for seamless schema migration.
  * Updated `AgenticTurnExecutor` and local GGUF streaming completion to parse thought blocks automatically into structured messages.
  * Fully integrated with `LogKeeper` for reasoning parsing telemetry without credentials or PII.
* Verification: Verified via `compile_applet` (clean Gradle build).
* Deviations: None.
* Follow-up: Ready for Mini-Phase 11.3 (Hierarchical Workspace Memory & Vector/BM25 Index).

* 2026-09-01T10:56:00-07:00
* Request: Mark current progress in Phase 11 specification file and update migration version note.
* Touched:
  * PHASE_11_BRAIN_AND_MEMORY.md
* Action:
  * Marked Mini-Phase 11.1 and Mini-Phase 11.2 as [COMPLETED].
  * Updated Mini-Phase 11.3 status to [IN QUEUE] and corrected the planned Room database migration to `MIGRATION_18_19` (DB version 19).
* Verification: Verified file edits.
* Deviations: None.
* Follow-up: Ready for Mini-Phase 11.3 (Modular Memory Store & Semantic Recall).

* 2026-09-02T15:40:00-07:00
* Request: Implement cursor-less voice-only input mode (no text input cursor while listening, dedicated voice-to-AI dispatch).
* Touched:
  * app/src/main/java/com/example/ui/chat/VoiceInputPulseBar.kt
  * app/src/main/java/com/example/ui/chat/ChatScreen.kt
* Action:
  * Added `onSendClick` action callback and Send action button (Icons.Default.ArrowUpward) to `VoiceInputPulseBar.kt`.
  * In `ChatScreen.kt`, concealed the text input box whenever `isListening` is active (`if (!isListening)`), eliminating the keyboard cursor and focusing the UI strictly on voice waveform and live transcript.
  * Dismissed software keyboard immediately when voice recording starts via `keyboardController?.hide()`.
  * Factored message submission and turn execution pipeline into reusable `dispatchPrompt` lambda, called by both the text send button and the voice pulse bar send button.
* Verification: Verified via `compile_applet` (clean compilation).
* Deviations: None.
* Follow-up: On-device testing of live STT and pulse bar send action.

* 2026-09-03T00:10:00-07:00
* Request: Enforce strict on-demand execution: single chosen AI model only unless specifically asked, only requested tools provided, remove unprompted background services.
* Touched:
  * app/src/main/java/com/example/MainActivity.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
  * app/src/main/java/com/example/engine/orchestrator/AgenticTurnExecutor.kt
* Action:
  * In `MainActivity.kt`: Removed unprompted automatic startup of `OmniRootProxyService` from `onCreate()`, preventing background HTTP proxy service and persistent notification from running continuously.
  * In `FallbackChainRouter.kt`: Eliminated automatic appending of unsolicited secondary providers from `ApiKeyDao` during candidate chain resolution. The router now strictly queries only the user's chosen AI model unless `targetModel` explicitly requests a fallback chain (`fallback/...`).
  * In `AgenticTurnExecutor.kt`: Upgraded `getActiveOmniTools()` to query workspace/thread integrations dynamically. Tools are provisioned on-demand, omit tool schemas entirely if disabled/none/off, and restrict to specific requested tools if configured, preventing unnecessary prompt bloat and unintended tool availability.
* Verification: Local build succeeded via `compile_applet`.
* Deviations: None.
* Follow-up: Verified zero unauthorized background daemons or silent multi-provider failovers.

* 2026-09-03T15:00:00-07:00
* Request: Refactor LogKeeper into headless catcher + rolling disk (2MB limit) with on-demand reader UI; implement encrypted backup and restore for chats, API keys, and settings with AES-256-GCM; add Google Account Chooser multi-account management flow for API services; connect Global Settings tool permissions to execution engine.
* Touched:
  * app/src/main/java/com/example/engine/tools/ToolPermissionManager.kt
  * app/src/main/java/com/example/MainActivity.kt
  * app/src/main/java/com/example/engine/orchestrator/AgenticTurnExecutor.kt
  * app/src/main/java/com/example/utils/LogKeeper.kt
  * app/src/main/java/com/example/ui/settings/LogKeeperScreen.kt
  * app/src/main/java/com/example/engine/db/ChatMessageDao.kt
  * app/src/main/java/com/example/engine/db/AiManagerDaos.kt
  * app/src/main/java/com/example/engine/db/WorkspaceConfigDao.kt
  * app/src/main/java/com/example/engine/db/ChatSettingsDao.kt
  * app/src/main/java/com/example/engine/backup/BackupManager.kt
  * app/src/main/java/com/example/ui/settings/SettingsPlaceholders.kt
  * app/src/main/java/com/example/ui/settings/omniroot/AiManagerViewModel.kt
  * app/src/main/java/com/example/ui/settings/omniroot/DirectToKeyWebViewScreen.kt
  * app/src/main/java/com/example/ui/settings/omniroot/AiManagerPanelScreen.kt
* Action:
  * In `LogKeeper.kt`: Removed in-memory `StateFlow<List<LogEntry>>` to eliminate RAM bloat. Built an asynchronous headless catcher streaming logs directly to disk with a 2MB rolling rotation limit (`omniroot_active_logs.jsonl` and `.old`). Retained a bounded in-memory ring buffer (50 items) strictly for emergency crash drops in Downloads. Added `readLogsFromDisk()` on-demand reader function.
  * In `LogKeeperScreen.kt`: Updated the UI to load logs on-demand from disk via coroutine reader, with manual Refresh and live signal tailing, eliminating permanent memory consumption while preserving live updates.
  * In `ToolPermissionManager.kt`, `MainActivity.kt`, and `AgenticTurnExecutor.kt`: Connected Global Settings tool toggles with persistent `SharedPreferences`, hard execution gating (`isForbidden`), and dynamic LLM schema pruning.
  * In Room DAOs: Added bulk retrieval and insertion queries for `ChatMessageDao`, `ApiKeyDao`, `WorkspaceConfigDao`, and `ChatSettingsDao`.
  * Created `BackupManager.kt`: Implemented AES-256-GCM authenticated encryption with PBKDF2 (10,000 iterations, 16-byte random salt, 12-byte IV) for encrypted backup exports and restores of chat histories, active/inactive API keys, workspace configurations, chat settings, and tool permissions.
  * In `SettingsPlaceholders.kt`: Connected the "Backup" and "Restore" buttons using Android Storage Access Framework (SAF) `CreateDocument` and `OpenDocument` launchers, passphrase dialogs with confirmation, and live progress indicators.
  * In `DirectToKeyWebViewScreen.kt`: Added a "Sign In with Google Account Chooser" button targeting `https://accounts.google.com/AccountChooser?continue=...` alongside direct console opening and quick Account Tag chips (`[Personal]`, `[Work]`, `[Account 2]`, `[Backup]`) to support switching and saving multiple accounts per provider.
  * In `AiManagerPanelScreen.kt` & `AiManagerViewModel.kt`: Added active account radio selector and deletion buttons to `ActiveKeysTab`, allowing users to switch active keys across multiple accounts seamlessly.
* Verification: Verified via `compile_applet` (clean Gradle build succeeded).
* Deviations: None.
* Follow-up: Perform on-device validation of SAF document picker and Google Account Chooser flow.

* 2026-09-03T15:15:00-07:00
* Request: Implement multi-account model binding and automatic 429 quota pooling for multiple accounts under the same AI service (e.g. Gemini Pro on Work account, Gemini Flash on Personal account; automatic key failover across account pool on rate limits).
* Touched:
  * app/src/main/java/com/example/engine/db/AiManagerDaos.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
  * app/src/main/java/com/example/engine/omniroot/service/OmniRootProxyServer.kt
  * app/src/main/java/com/example/ui/settings/omniroot/TranslatorTab.kt
* Action:
  * In `AiManagerDaos.kt`: Added `getKeysForProviderList(providerId)` and `getKeyById(id)` to `ApiKeyDao` for synchronous/coroutine multi-account key lookups, and added `deleteChain` to `FallbackChainDao`.
  * In `FallbackChainRouter.kt`: Created `CandidateNode` supporting per-model account binding syntax (e.g. `provider/model@accountAlias` or `#keyId`), as well as structured JSON objects. Implemented `executeProviderCallWithAccountPool()`: automatically resolves candidate keys for a provider (prioritizing preferred account, then active key, then others) and catches HTTP 429 / `RESOURCE_EXHAUSTED` / quota errors to automatically pool/failover across all available account keys before falling back to lower-tier models. Recorded `actualAccountAlias` in `FallbackExecutionResult`.
  * In `OmniRootProxyServer.kt`: Updated proxy model parsing to support `@account` binding and candidate key failovers on HTTP 429 across the provider's account pool.
  * In `TranslatorTab.kt`: Updated fallback chains UI to display account badges (e.g. `Account: Work` vs `Account: Auto Pool`), added delete button for chains, and updated the "New Fallback Chain" dialog to allow optional per-priority account alias binding.
* Verification: Verified via `compile_applet` (Gradle compilation succeeded).
* Deviations: None.
* Follow-up: On-device testing of fallback chain execution and rate-limit auto-failover across multiple accounts.

* 2026-09-03T15:55:00-07:00
* Request: Integrate verified zero-card free-tier AI providers (Cerebras, Mistral, SambaNova, Hugging Face, Cohere) into native routing, Direct-to-Key setup, and combo presets.
* Touched:
  * app/src/main/java/com/example/engine/db/ProviderPrepopulator.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
  * app/src/main/java/com/example/engine/omniroot/service/OmniRootProxyServer.kt
  * app/src/main/java/com/example/ui/settings/omniroot/DirectToKeyWebViewScreen.kt
  * app/src/main/java/com/example/ui/settings/omniroot/TranslatorTab.kt
  * BLUEPRINT.md
* Action:
  * In `ProviderPrepopulator.kt`: Added verified zero-payment free tier providers (`cerebras`, `mistral`, `sambanova`, `huggingface`, `cohere`) with exact endpoints, free tier descriptions, and direct login URLs.
  * In `FallbackChainRouter.kt`: Added endpoint base URL and payload mappings for Cerebras, Mistral, SambaNova, Hugging Face, and Cohere. They seamlessly share the existing `OPENAI` translation logic and automatic multi-account 429 quota pooling.
  * In `OmniRootProxyServer.kt`: Added endpoint and target format mappings for all newly added providers so external dev tools targeting `localhost:8080` can seamlessly route to them.
  * In `DirectToKeyWebViewScreen.kt`: Added tailored key labels, format placeholders, and supporting guidance text for Cerebras (1M tokens/day), Mistral (Experiment tier), SambaNova (200k tokens/day), Hugging Face (read token), and Cohere (Trial key).
  * In `TranslatorTab.kt`: Added 1-tap zero-card preset chain buttons ("Zero-Cost Ultra Speed" pairing Groq, Cerebras, and Gemini Flash; and "Zero-Cost Frontier Coding" pairing Gemini Pro, Mistral Codestral, and OpenRouter Llama 3.3 Free).
  * In `BLUEPRINT.md`: Documented zero-card free tier provider integrations and preset combo routing.
* Verification: Verified via `compile_applet` (clean Gradle compilation succeeded).
* Deviations: None.
* Follow-up: Perform on-device test of 1-tap preset creation in Translator Tab and key entry in Direct-to-Key screen.

* 2026-09-03T23:51:30-07:00
* Request: Add GLHF AI (`glhf`) to verified zero-card providers.
* Touched:
  * app/src/main/java/com/example/engine/db/ProviderPrepopulator.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
  * app/src/main/java/com/example/engine/omniroot/service/OmniRootProxyServer.kt
  * app/src/main/java/com/example/ui/settings/omniroot/DirectToKeyWebViewScreen.kt
  * BLUEPRINT.md
* Action:
  * In `ProviderPrepopulator.kt`: Registered GLHF AI (`glhf`) with `baseUrl = "https://glhf.chat/api/openai/v1"` and `loginUrl = "https://glhf.chat/users/settings/api"`.
  * In `FallbackChainRouter.kt`: Added GLHF endpoint mapping targeting `https://glhf.chat/api/openai/v1/chat/completions` using standard OpenAI payload format.
  * In `OmniRootProxyServer.kt`: Added GLHF proxy endpoint mapping for external developer clients targeting `localhost:8080`.
  * In `DirectToKeyWebViewScreen.kt`: Added placeholder (`glhf_...`), custom label, and direct guidance for zero-card developer beta key setup.
  * In `BLUEPRINT.md`: Added GLHF AI to verified zero-card provider documentation.
* Verification: Verified via `compile_applet` (clean build succeeded).
* Deviations: None.
* Follow-up: Test adding a GLHF key in the Direct-to-Key screen and executing a chat turn.

* 2026-09-04T14:17:00-07:00
* Request: Implement Skills and Plugins Global Settings UI with search bar in tools/mcps, FAB creation form, Functional vs Soul skills, multi-selector plugin bundling (skills, tools, MCPs, instructions, restrictions), and remote URL importing.
* Touched:
  * app/src/main/java/com/example/ui/settings/SettingsPlaceholders.kt
  * app/src/main/java/com/example/engine/skills/SkillEntity.kt
  * app/src/main/java/com/example/engine/skills/SkillManager.kt
  * app/src/main/java/com/example/engine/plugins/PluginManager.kt
  * BLUEPRINT.md
* Action:
  * In `SettingsPlaceholders.kt`: Implemented full `SkillsSettingsContent` UI featuring search bar, Functional vs Soul/Persona tabs, FAB dialog for creating/editing skills, and remote URL import dialog.
  * In `SettingsPlaceholders.kt`: Implemented full `PluginsSettingsContent` UI featuring search bar, FAB dialog with multi-tab selector (Info, Skills multi-check, Tools multi-check, MCP servers multi-check), custom prompt instructions, strict guardrails/restrictions, and remote plugin manifest JSON import.
  * In `SettingsPlaceholders.kt`: Added explicit search filtering to `MCPSettingsContent` for configured servers, accounts, and endpoints.
  * In `SkillEntity.kt`: Added `override` keyword to properties implementing `Skill` interface (`name`, `description`, `instructions`).
  * In `SkillManager.kt` and `PluginManager.kt`: Corrected `LogKeeper` import package from `com.example.util` to `com.example.utils`.
  * In `BLUEPRINT.md`: Documented completed Skills and Plugins UI implementation under Phase 6.
* Verification: Verified via local compilation task (`gradle :app:compileDebugKotlin` completed with BUILD SUCCESSFUL).
* Deviations: None.
* Follow-up: Perform on-device test of skill creation, soul persona toggle, plugin multi-selector bundling, and search filtering.

* 2026-09-04T14:46:00-07:00
* Request: Implement Global Settings screens for Code Editor, Permissions, Typography/Font, Library Management (blank till GDrive connected with CTA to connect), and wire Encrypted Backup & Restore.
* Touched:
  * app/src/main/java/com/example/ui/settings/SettingsPlaceholders.kt
  * app/src/main/java/com/example/ui/OmniRootApp.kt
  * BLUEPRINT.md
* Action:
  * In `SettingsPlaceholders.kt`: Implemented `PermissionsSettingsContent` with live permission status checks for Microphone, Notifications, and Media/Storage, dynamic in-app permission request launcher, and a shortcut button to Android system application settings.
  * In `SettingsPlaceholders.kt`: Implemented `FontSettingsContent` with app typeface family choices (Default, Sans-Serif, Serif, Monospace), font scale slider (0.85x to 1.30x), persistent SharedPreferences storage, and a live typography preview card.
  * In `SettingsPlaceholders.kt`: Implemented `LibrarySettingsContent` with GDrive connection gate. When disconnected, displays a clean empty state card with 'Google Drive Not Connected' information and 'Connect Google Drive' CTA dialog; when connected, lists synced component folders and provides disconnect action.
  * In `SettingsPlaceholders.kt`: Implemented `EditorSettingsContent` with code font size slider (10-24sp), tab spacing segmented control (2, 4, 8 spaces), toggles for line numbers, word wrap, auto-indent, and bracket matching, and a live code preview window.
  * In `OmniRootApp.kt`: Registered composable routes for `settings/editor`, `settings/permissions`, `settings/font`, `settings/library`, and `settings/backup` (wiring to existing authenticated AES-256-GCM `BackupSettingsContent`).
  * In `BLUEPRINT.md`: Updated Phase 6 ledger to document the completed settings pages.
* Verification: Local build verified via `gradle :app:compileDebugKotlin` (BUILD SUCCESSFUL in 58s).
* Deviations: None.
* Follow-up: Verify on-device navigation to each settings screen from the Global Settings menu.

* 2026-09-04T15:21:00-07:00
* Request: Implement Omnivian Artifact Provider System (Room entity with v21 migration, KeyStore passkey encryption with Room SHA-256 hash, live artifact source of truth resyncing, ArtifactBridge @JavascriptInterface with MainLooper thread marshaling, ArtifactWebView headless wrapper, ArtifactProviderPool with LRU concurrency cap and 30s polling, ArtifactRouter integration with FallbackChainRouter, Provider Management UI tab in AI Manager, and General Settings screen in Global Settings).
* Touched:
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactKeyStore.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactProviderEntity.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactProviderDao.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactBridge.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactWebView.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactProviderPool.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactRouter.kt
  * app/src/main/java/com/example/engine/db/AppDatabase.kt
  * app/src/main/java/com/example/engine/router/FallbackChainRouter.kt
  * app/src/main/java/com/example/ui/settings/GeneralSettingsContent.kt
  * app/src/main/java/com/example/ui/settings/omniroot/ArtifactProvidersTab.kt
  * app/src/main/java/com/example/ui/settings/omniroot/AiManagerPanelScreen.kt
  * app/src/main/java/com/example/ui/settings/GlobalSettingsScreen.kt
  * app/src/main/java/com/example/ui/OmniRootApp.kt
  * BLUEPRINT.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Created `ArtifactKeyStore.kt`: Implemented AndroidKeyStore hardware-backed AES-256-GCM encryption for original passkeys; provided `hashPasskey()` generating SHA-256 for Room entity storage.
  * Created `ArtifactProviderEntity.kt` & `ArtifactProviderDao.kt`: Stored provider metadata, priority, enabled state, SHA-256 hash, and cached token metrics.
  * Updated `AppDatabase.kt`: Bumped Room version to 21, registered `ArtifactProviderEntity`, exposed `artifactProviderDao()`, and implemented `MIGRATION_20_21` safely creating `artifact_providers` table.
  * Created `ArtifactBridge.kt`: Implemented `@JavascriptInterface` bridge with thread marshaling to `Looper.getMainLooper()`, callback listener, streaming chunks, and passkey request provider.
  * Created `ArtifactWebView.kt`: Implemented headless Android WebView wrapper with zero-trust local file access sandbox, postMessage relay script injection, queryState evaluation, and exponential backoff crash recovery.
  * Created `ArtifactProviderPool.kt`: Implemented singleton pool with user-configurable concurrency cap from `general_prefs`, startup and 30s interval `getState()` polling to maintain the live artifact as the source of truth, and `getBestAvailable()` provider picker.
  * Created `ArtifactRouter.kt`: Implemented OmniRoot routing delegating prompts to pool WebViews, streaming tokens, and assembling `OmniResponse`.
  * Updated `FallbackChainRouter.kt`: Hooked `artifact` and `omnivian` provider IDs to route requests through `ArtifactRouter`.
  * Created `GeneralSettingsContent.kt`: Implemented UI for max concurrent WebViews slider (1-4) with RAM safety warning, generation timeout (30-180s), auto-reload and auto-sync toggles, and cache purge action.
  * Created `ArtifactProvidersTab.kt`: Built provider management UI with status indicators, live token consumption bars, force reload, passkey editing/unlocking, and deletion.
  * Updated `AiManagerPanelScreen.kt`: Added "Artifacts" tab to top tab row.
  * Updated `GlobalSettingsScreen.kt` & `OmniRootApp.kt`: Added "General" item under "App & IDE" with dedicated `settings/general` composable route.
  * Updated `BLUEPRINT.md`: Documented all completed components under Phase 6 and Phase 9.
* Verification: Verified via `compile_applet` (clean build succeeded).
* Deviations: None.
* Follow-up: Perform on-device verification of artifact registration, auto-unlock handshake, and concurrency adjustments.

* 2026-09-05T12:36:00-07:00
* Request: Create new plan file for modular engines and multi-workspace architecture.
* Touched:
  * /MODULAR_ENGINE_WORKSPACE_PLAN.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Drafted comprehensive `/MODULAR_ENGINE_WORKSPACE_PLAN.md` documenting the headless AI-first / UI-second on-demand engine architecture.
  * Formalized file-explorer-centric workspace isolation, cross-workspace file transfers, 3-tier artifact progression, and initial 4 core engines (Code with separate Diff, Document with tables/slides, Image with spatial region selector, and Browser).
  * Documented dynamic HTML5/Wasm sandboxed engine imports, MCP hub integration, Global Settings > Engines management, and the AI tab permission guardrail (`request_open_tab`).
* Verification: File creation verified on disk. No code or build touched per discussion mandate.
* Deviations: None.
* Follow-up: Await user review of plan before entering implementation phase.

* 2026-09-05T15:55:00-07:00
* Request: Make new plan file for macro-engine host skeletons, Google Play DCL compliance, "view first" lifecycle, and AI adaptation lab.
* Touched:
  * /MACRO_ENGINE_HOST_PLAN.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Created `/MACRO_ENGINE_HOST_PLAN.md` formalizing the 5 native host skeletons (Text, Document, Media, Spatial, Sandbox WebView).
  * Documented the 3-phase progression: View First (Now) -> AI Interactive (Next) -> Full Editor (Later).
  * Detailed the View First matrix for Code, PDF (native `PdfRenderer`), Zip (virtual tree), Presentations, Markdown, Tables, Images, and Browser.
  * Defined the AI Ingestion & Adaptation Lab pipeline and strict cross-engine permission boundaries.
* Verification: File creation verified on disk. No code or build touched per discussion mandate.
* Deviations: None.
* Follow-up: Await user approval and instruction before any code modification.

* 2026-09-06T00:36:00-07:00
* Request: Make master file for workspace tabs architecture and mark all other draft workspace plan files as not chosen.
* Touched:
  * /MASTER_WORKSPACE_TAB_PLAN.md
  * /MULTI_WORK_TAB_ENGINE_PLAN.md
  * /MODULAR_ENGINE_WORKSPACE_PLAN.md
  * /MACRO_ENGINE_HOST_PLAN.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Created `/MASTER_WORKSPACE_TAB_PLAN.md` as the official chosen architecture.
  * Marked `MULTI_WORK_TAB_ENGINE_PLAN.md`, `MODULAR_ENGINE_WORKSPACE_PLAN.md`, and `MACRO_ENGINE_HOST_PLAN.md` as `NOT CHOSEN / SUPERSEDED`.
  * Synthesized the complete File Explorer base foundation, "Workspace Tab" nomenclature, viewer lifecycle (View -> Inspect/Point -> Edit), 3-tier artifact progression (Inline -> Full Artifact -> Workspace Tab), engine composability (mixing viewers + toolsets), and the dedicated AI Adaptation Lab.
* Verification: File creations and status markers verified on disk. No application code or build touched per discussion mandate.
* Deviations: None.
* Follow-up: Ready for future blueprinting when user triggers implementation phase.

* 2026-09-06T00:53:00-07:00
* Request: Update plan file with concrete integration details into existing codebase.
* Touched:
  * /MASTER_WORKSPACE_TAB_PLAN.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Updated `/MASTER_WORKSPACE_TAB_PLAN.md` to map directly to existing codebase touchpoints:
    * Retained the Right Drawer File Explorer pattern (`FileExplorerDrawerContent` in `CodeScreen.kt`).
    * Replaced the static single editor area with a dynamic Workspace Tab host swapping viewers by file extension.
    * Added horizontal bottom variation carousel specifically for workspaces that need it.
    * Expanded the artifact export workflow (`ArtifactBottomSheet.kt`) from "Open in Code" to "Open in Work Tab".
    * Mapped workspace presets into `NewChatDialog.kt` focus selector.
    * Hooked new workspace tools into existing `WorkspaceActions.kt` / `OmniRootRouter.kt` registry.
    * Placed engine CRUD and package import/export directly into `GlobalSettingsScreen.kt` under General (`settings/general`).
* Verification: File updated cleanly. No Kotlin/app code touched per discussion mandate.
* Deviations: None.
* Follow-up: Await user instruction.

* 2026-09-06T05:04:00-07:00
* Request: Update MASTER_WORKSPACE_TAB_PLAN.md with view-only artifact workflow, edit fork action, knife-set tool boundaries, and 3D/maps support.
* Touched:
  * /MASTER_WORKSPACE_TAB_PLAN.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Updated `/MASTER_WORKSPACE_TAB_PLAN.md`:
    * Formalized that all artifacts are strictly view-only.
    * Detailed top bar with Web App Preview/Code toggle, save restricted to webapps/sites for the Global Sidebar Artifacts page, and `[Edit]` fork bridge (Current Chat Tab vs New Chat Workspace).
    * Reused Engine Host viewers for non-web artifacts (Code, Docs/PDF, Images, 3D glTF, GeoJSON Maps, Audio/Video).
    * Added "Knife-Set" tool allocation model (Utility knife global, Chef/Cleaver/Paring/Scalpel domain sets) with hard-blocked permission guardrail when switching tool sets.
* Verification: File updated cleanly. No application code or build touched per discussion mandate.
* Deviations: None.
* Follow-up: Ready for future blueprinting when user triggers implementation.

* 2026-09-08T13:36:00-07:00
* Request: Update master workspace plan with native base engines (Code, Docs, Video, Image, Audio, SQLite) and Obsidian-style extensible on-demand WebView modules for 3D and Maps.
* Touched:
  * /WORKSPACE_AND_EXTRA_PLANS.md
  * /receipts/RECEIPTS_117.md
* Action:
  * Updated Section 4 in `/WORKSPACE_AND_EXTRA_PLANS.md`:
    * Formalized Tier 1: Built-in Native Android Engines (Compose Code editor, Markdown/PDF compiler, hardware MediaExtractor/Muxer video trimmer, Skia image canvas, AudioTrack PCM synth, SQLite FTS5) guaranteeing 0MB APK bloat and native silicon performance.
    * Formalized Tier 2: Extensible Modular WebView (Obsidian plugin model): ultra-lightweight base `stage.html` (<10KB) with strictly on-demand dynamic module injection for 3D (`spatial_3d`), Maps/GIS (`interactive_maps`), and Diagrams (`diagram_canvas`), with immediate VRAM/DOM cleanup on navigation.
  * Updated Section 9 Functional Bundles:
    * Bundle B: Updated to include native base editors (CodeEditorPane, AndroidPdfCompiler, NativeMediaEngine, SkiaCanvasRenderer, AudioSynthEngine, SqliteWorkspaceEngine).
    * Bundle C: Updated HardenedWebViewManager with Obsidian-style on-demand module loader (`OmnivianBridge.loadModule`).
* Verification: File updated cleanly. No application code or build executed per discussion mandate.
* Deviations: None.
* Follow-up: Ready for implementation when user triggers.

* 2026-09-09T11:05:00-07:00
* Request: Implement TWO_PART_ARTIFACT_SECRETS_PLAN.md (Mini-Phases 1 through 5) and resolve hardcoded keystore credentials.
* Touched:
  * app/src/main/java/com/example/engine/settings/ThreadSecretsStore.kt
  * app/src/main/java/com/example/engine/omniroot/artifact/ArtifactKeyStore.kt
  * app/src/main/java/com/example/ui/settings/ThreadSettingsScreen.kt
  * app/src/main/java/com/example/ui/chat/PWAPreviewBottomSheet.kt
  * app/src/main/java/com/example/engine/fs/ArtifactWorkspaceManager.kt
  * app/src/main/java/com/example/ui/artifacts/ArtifactsScreen.kt
  * app/src/main/java/com/example/engine/export/HardenedExportManager.kt
  * app/src/main/java/com/example/ui/export/GithubExportBottomSheet.kt
  * app/build.gradle.kts
  * BLUEPRINT.md
* Action:
  * Created `ThreadSecretsStore.kt`: Isolated workspace-scoped secrets store in private `SharedPreferences` (`prefs_thread_secrets_<ws>`).
  * Updated `ArtifactKeyStore.kt`: Implemented AES-256-GCM authenticated encryption/decryption for strings (`encryptString`, `decryptString`).
  * Updated `ThreadSettingsScreen.kt` (Mini-Phase 1): Added "Integrations 🔌" and "Secrets 🔑" tabs to manage integrations and workspace secrets with key-level masking and LogKeeper audit events.
  * Updated `PWAPreviewBottomSheet.kt` & `ArtifactsScreen.kt` (Mini-Phase 2): Added runtime blind in-memory injection of `window.__SECRETS__ = Object.freeze(...)` with `'secretsready'` CustomEvent in WebViews.
  * Updated `ArtifactWorkspaceManager.kt` (Mini-Phases 3 & 4): Implemented 2-part packaging (`package2PartArtifact`) storing Part 1 code in `/artifacts/<id>/repo/` and Part 2 in encrypted companion file `/artifacts/<id>/secrets_bundle.enc`. Implemented `openArtifactInWorkspace` and `forkWorkspaceToNewArtifact` ensuring Part 2 secrets are strictly withheld from workspace directories and directly populated into private `SharedPreferences`.
  * Updated `ArtifactsScreen.kt` (Mini-Phase 4): Added 1-tap "Fork" button in `OpenedArtifactViewerDialog` preserving thread secrets across forks.
  * Created `HardenedExportManager.kt` & updated `GithubExportBottomSheet.kt` (Mini-Phase 5): Implemented export sanitizer stripping `*.enc`, `.env*`, and credential tokens, generating blank `secrets.example.json` templates, displaying a zero-secret security indicator, and logging audit events to `LogKeeper`.
  * Remediated Keystore Credentials in `app/build.gradle.kts`: Removed hardcoded `storePassword = "android"` and `keyPassword = "android"` from `build.gradle.kts`, switching debug signing to standard AGP built-in `signingConfigs.getByName("debug")`.
  * Updated `BLUEPRINT.md`: Documented completed Phase 8.1 2-Part Artifact Secrets System.
* Verification: Verified via `compile_applet` (clean Gradle build succeeded).
* Deviations: None.
* Follow-up: Provide user on-device verification suite.

* 2026-09-16T02:54:00-07:00
* Request: Implement backup and restore for Claude artifact providers and their encrypted passkeys.
* Touched:
  * app/src/main/java/com/example/engine/backup/BackupManager.kt
  * app/src/main/java/com/example/ui/settings/SettingsPlaceholders.kt
  * /receipts/RECEIPTS_117.md
* Action:
  * Updated `BackupStats` and `RestoreStats` data classes with `artifactProviderCount: Int = 0`.
  * Updated `BackupManager.exportBackup`:
    * Added retrieval of `artifact_providers` from `db.artifactProviderDao().getAll()`.
    * Serialized provider records (id, name, url, owner, hashedPasskey, priority, enabled, session stats, timestamps) into the root encrypted JSON payload.
    * Retrieved and decrypted original passkeys from hardware `ArtifactKeyStore` to safely bundle inside the authenticated AES-256-GCM ciphertext (`rawPasskey`).
  * Updated `BackupManager.restoreBackup`:
    * Deserialized `artifact_providers` from decrypted JSON payload.
    * Re-inserted records into Room via `db.artifactProviderDao().insert()`.
    * Re-saved hardware/AES passkeys into `ArtifactKeyStore.savePasskey()` for each provider.
    * Re-registered providers in Room's `ai_models` database table for chat model picker discovery.
    * Triggered `ArtifactProviderPool.resyncAllStates()` to refresh the active webview provider pool.
  * Updated `SettingsPlaceholders.kt`:
    * Expanded "Backup Scope" UI card to explicitly list Claude.ai Headless Artifact Providers and encrypted passkeys.
    * Updated backup and restore toast messages to report artifact provider counts.
* Verification: Verified via `compile_applet` (Gradle build succeeded cleanly).
* Deviations: None.
* Follow-up: Verified on-device manual QA flow provided.

* 2026-09-17T15:21:00-07:00
* Request: Implement Mini-Phase 1 (In-Chat Non-Text Artifact Cards & Shared Media Pool).
* Touched:
  * app/src/main/java/com/example/engine/media/ExoPlayerPool.kt
  * app/src/main/java/com/example/ui/chat/ArtifactChatCard.kt
  * app/src/main/java/com/example/ui/chat/ArtifactPreviewSheet.kt
  * app/src/main/java/com/example/ui/chat/ChatScreen.kt
  * app/src/main/java/com/example/ui/chat/ToolCallBubble.kt
  * app/src/main/java/com/example/ui/OmniRootApp.kt
* Action:
  * Created `ExoPlayerPool.kt`: Lifecycle-safe singleton pool managing recycled `MediaPlayer` playback instances for audio/video cards to prevent audio track exhaustion and collision across the chat list. Connected to `LogKeeper`.
  * Created `ArtifactChatCard.kt`: Universal Tier-1 In-Chat representation for all non-text outputs (HTML Web Apps, Audio, Video, Images, Slides/PPT, Documents/PDF, 3D Models, Code Files, Generic Files). Added inline audio scrubber/controls, video card, presentation badge, image viewer preview, and direct action buttons for "Workspace" and "Preview".
  * Implemented `ArtifactExtractor`: Scans tool args, file modification history, and message text to infer artifact types and build `InChatArtifactInfo` models.
  * In `ArtifactPreviewSheet.kt`: Provided full-screen preview dialog with hardware-accelerated video/audio playback, webview bridge for web apps, zoomable image rendering, and document slide inspector. Fixed layout alignment to `CenterHorizontally`.
  * In `ChatScreen.kt`: Integrated inline artifact cards into `AiMessage`, `AppActionMessage`, and `ActionHistoryCard`. Added `previewingArtifact` state dialog, wired `onOpenInWorkspace` callback to switch directly to the Code/Workspace tab, and registered `ExoPlayerPool.release()` in `DisposableEffect`.
  * In `ToolCallBubble.kt`: Rendered inline `ArtifactChatCard` items for tool-generated artifacts using Compose-safe iteration.
  * In `OmniRootApp.kt`: Wired `onOpenInWorkspace` callback from `ChatScreen` to switch `currentTab` directly to `AppTab.CODE`.
* Verification: Verified via `compile_applet` (clean Gradle build succeeded).
* Deviations: None.
* Follow-up: Mini-Phase 1 verified and operational. Mini-Phase 2 (Polymorphic Bottom Sheet & Standalone Multitasking) ready.

* 2026-09-19T23:59:00-07:00
* Request: Move all current plan /phases file (overall and this modular workspace) into folder called current plan.
* Touched:
  * current plan/ (directory created)
  * current plan/BLUEPRINT.md
  * current plan/WORKSPACE_AND_EXTRA_PLANS.md
  * current plan/MODULAR_WORKSPACE_AND_EXTRAS_BUILD_PLAN.md
  * current plan/MODULAR_WORKSPACE_VIEWER_PLAN.md
  * current plan/MODULAR_ENGINE_WORKSPACE_PLAN.md
  * current plan/MACRO_ENGINE_HOST_PLAN.md
  * current plan/MULTI_WORK_TAB_ENGINE_PLAN.md
  * current plan/TWO_PART_ARTIFACT_SECRETS_PLAN.md
  * current plan/PHASE_4_BLUEPRINT.md
  * current plan/PHASE_5_BLUEPRINT.md
  * current plan/PHASE_5_CODE_EDITOR_PLAN.md
  * current plan/PHASE_8_9_PWA_SIDEBAR_PROVIDER.md
  * current plan/PHASE_8_MINI_PHASES.md
  * current plan/PHASE_9_5_STREAMING.md
  * current plan/PHASE_9_OMNIROUTE.md
  * current plan/PHASE_10_TOOLS_AND_SANDBOXES.md
  * current plan/PHASE_11_BRAIN_AND_MEMORY.md
  * current plan/PHASE_15_WEB_AI_HYBRID.md
  * current plan/PHASE_TEMP_SHORTCUTS_ARTIFACTS_VOICE.md
  * current plan/new_things_to_add.md
* Action:
  * Created `current plan/` directory.
  * Relocated all 20 plan and phase specification documents (master blueprint, overall workspace & extras plans, active modular workspace build roadmap, and all historical phase blueprints/plans) into `current plan/`.
  * Verified root directory and `current plan/` folder contents.
* Verification: Verified directory listings via `list_dir`.
* Deviations: None.
* Follow-up: Ready for next instructions or Mini-Phase 3 execution.








