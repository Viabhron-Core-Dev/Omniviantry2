# PHASE 11: The Brain, Memory & Agent Ecosystem Specification

## Architectural Overview
Phase 11 implements the cognitive core, modular persistent memory, structured multi-agent reasoning, surgical search-and-replace file manipulation, and unified diff engine for Omnivian. Every component is directly integrated with the project's original `LogKeeper` (`com.example.utils.LogKeeper`) to record diagnostics, error types, timestamps, and stack traces with zero PII or credentials.

---

## Mini-Phase Breakdown

### Mini-Phase 11.1: Context Compressor & Token Budget Engine [COMPLETED]
- **`ContextBudgetManager.kt`**: Token estimation and budget tracker per model family (Gemini, Claude, OpenAI, Local GGUF).
- **`SlidingWindowPruner.kt`**: Preserves core system prompts, active tool sequences, and recent dialogue turns while gracefully compacting or summarizing stale message histories.
- **`SnippetCompactor.kt`**: Truncates large file contents into relevant line windows when context pressure exceeds 80% threshold.
- **LogKeeper Integration**:
  - `LogKeeper.log("ContextBudgetManager", "Pruning", "Compressed message history from X tokens to Y tokens")`
  - `LogKeeper.log("ContextBudgetManager", "OverflowWarning", "Context limit pressure at Z%")`

---

### Mini-Phase 11.2: Chain-of-Thought (CoT) & Structured Reasoning Parser [COMPLETED]
- **`ReasoningParser.kt`**: Streaming parser intercepting `<thought>...</thought>`, `<thinking>...</thinking>`, and `<plan>...</plan>` tags in real-time.
- **`ReasoningState` / `ReasoningParseResult`**: Data models exposing real-time thinking status (`thoughtContent`, `planContent`, `cleanAnswer`, `hasThinking`, `thinkingDurationMs`).
- **`ThoughtBubble.kt`**: Expandable Material 3 Jetpack Compose UI component in the chat timeline showing elapsed thinking time (e.g. "Thought for 3.8s") with collapsible markdown rendering.
- **Database Schema**: Added `thoughtContent`, `planContent`, and `thinkingDurationMs` to `ChatMessageEntity` via `MIGRATION_17_18` (DB version 18).
- **LogKeeper Integration**:
  - `LogKeeper.log("ReasoningParser", "StateTransition", "Parsed reasoning state with X ms elapsed")`
  - `LogKeeper.log("ReasoningParser", "TagMismatch", "Malformed or unclosed reasoning tag handled gracefully")`

---

### Mini-Phase 11.3: Modular Memory Store & Semantic Recall (Room DB) [IN QUEUE]
- **`MemoryEntity.kt`**:
  - Fields: `id: Long`, `key: String`, `content: String`, `category: String` (`USER_PREFERENCE`, `PROJECT_RULE`, `ARCHITECTURAL_DECISION`, `SNIPPET`), `relevanceScore: Float`, `createdAt: Long`, `lastAccessedAt: Long`, `isPinned: Boolean`.
- **`MemoryDao.kt`**: Keyword searching, category filtering, access timestamp tracking, pin/unpin toggles, and deletion.
- **Room Database Migration (`MIGRATION_18_19`)**: Adds the `memories` table to `AppDatabase.kt` (DB version 19).
- **`MemoryRepository.kt`**: Repository abstraction for memory storage, keyword scoring, and semantic retrieval.
- **LogKeeper Integration**:
  - `LogKeeper.log("MemoryRepository", "RecallQuery", "Queried memories for keywords; found N matches")`
  - `LogKeeper.log("MemoryRepository", "SaveMemory", "Persisted memory key=X category=Y")`

---

### Mini-Phase 11.4: Dynamic Context & System Prompt Injector
- **`PromptAssembler.kt`**:
  - Injects base identity and developer guidelines.
  - Reads root `AGENTS.md` and `GEMINI.md` rules from the active workspace.
  - Queries `MemoryRepository` for relevant memories matching the current prompt.
  - Formats active tool definitions and permission rules.
- **`ContextAssemblyResult`**: Structured assembly payload with system prompt and pruned message history.
- **LogKeeper Integration**:
  - `LogKeeper.log("PromptAssembler", "AssemblyComplete", "Assembled prompt with N injected memories and N tools")`

---

### Mini-Phase 11.5: Surgical Search & Replace Tool (`edit_file`)
- **`SurgicalEditor.kt`**:
  - Exact target substring matching with strict uniqueness verification.
  - Whitespace and line-ending normalization (`\r\n` vs `\n`).
  - Multi-chunk atomic replacement (all chunks apply or rollback).
  - Pre-write validation and automatic in-memory rollback on failure.
- **`edit_file` Tool Registration**: Registered in `EngineRegistry` with typed parameters (`path`, `target_text`, `replacement_text`).
- **LogKeeper Integration**:
  - `LogKeeper.log("SurgicalEditor", "EditSuccess", "Applied N replacement chunks to file X")`
  - `LogKeeper.log("SurgicalEditor", "EditFailed", "Target content not unique or not found in file X")`

---

### Mini-Phase 11.6: Unified Diff & Patch Engine
- **`UnifiedDiffParser.kt`**: Parses standard Git / unified diff syntax (`@@ -x,y +a,b @@`) into structured `DiffHunk` and `DiffOperation` objects.
- **`PatchApplier.kt`**: Applies hunks line-by-line with fuzzy line offset matching (+/- 3 lines) if lines shifted.
- **`DiffPreviewCard.kt`**: Compose UI component with red deletions, green additions, and a 1-tap "Review & Apply" preview.
- **LogKeeper Integration**:
  - `LogKeeper.log("PatchApplier", "PatchSuccess", "Applied patch with N hunks to file X")`
  - `LogKeeper.log("PatchApplier", "HunkConflict", "Failed to match hunk header in file X at line Y")`

---

### Mini-Phase 11.7: Brain & Memory Management UI
- **`MemoryManagementScreen.kt`**:
  - Search bar and category filter chips (`All`, `Rules`, `Preferences`, `Decisions`).
  - Add, edit, pin, and delete dialogs.
- **`BrainSettingsCard.kt`**:
  - "Show Thinking Process" toggle in chat settings.
  - Memory recall threshold slider.
  - Active token budget and context pressure preview bar.
- **LogKeeper Integration**:
  - `LogKeeper.log("MemoryUI", "UserEdit", "User manually modified memory id=X")`

---

## LogKeeper Standard Verification
All modules must call `LogKeeper.log(tag, action, message)` on every state transition, failure, or security check, maintaining 100% compliance with zero PII and zero credential logging.
