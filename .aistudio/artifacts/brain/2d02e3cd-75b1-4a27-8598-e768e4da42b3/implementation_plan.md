# OmniRoot Modern UI & Experience Redesign

A cohesive design and architectural plan to elevate the OmniRoot interface to modern commercial standards (inspired by Claude Artifacts, Google AI Studio, ChatGPT, and Gemini) while retaining all existing action buttons, placement hierarchies, and defaulting to a crisp, high-contrast Light Theme.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during interactive clarification and form the foundation of this redesign:

- **Primary Navigation (Tabs)**: Retain the bottom pill toggle for `Chat` and `Code` plus the more-options menu button in their existing layout, upgraded with sleek pill geometry, active indicator animations, tonal container surfaces, and haptic feedback.
- **File Explorer & Workspace**: Replace the cramped slide-out panel with an expansive, full-screen modern file manager featuring collapsible directory cards, breadcrumbs, search filtering, and file-type badge iconography.
- **AI Action Steps**: Implement collapsible Google AI Studio-style action step pills immediately following assistant responses, summarizing tool executions (file reads/writes, web search, terminal commands) and token metrics with an expandable audit trail.
- **Gemini Attachment Sheet**: Redesign the attachment picker into a clean 2-row grid of rounded squircle action tiles with distinctive pastel icons and subtle micro-interactions.
- **Default Theme**: Enforce an intentional, modern **Light Theme** as the application default (subtle off-white background `#F8FAFC`, crisp slate card borders, refined typography, and indigo/violet primary accents) with seamless dark mode support.
- **Button Placement Fidelity**: Strict zero-drift preservation of all current buttons, floating action buttons (LogKeeper bug report), top-bar action menus, and camera/mic inputs.

---

## 1. Overview & Core Concept

### What It Delivers
This upgrade addresses visual clutter, friction, and navigation stiffness in the current chat and workspace experience. It transforms OmniRoot into a refined developer and AI environment combining:
1. The spacious, readable message presentation of **ChatGPT**.
2. The interactive, multi-view inspection and code preview capabilities of **Claude Artifacts**.
3. The granular execution transparency and collapsible run actions of **Google AI Studio**.
4. The rapid, touch-friendly attachment sheet design of **Google Gemini**.

### Target Audience & Persona
Power users, software engineers, and AI developers navigating complex multi-file projects, inspecting tool execution runs, and generating code on mobile and tablet devices.

---

## 2. User Experience & Visual Design

### Key User Flows

#### A. Chat & Conversation Flow (ChatGPT + Google AI Studio Hybrid)
1. **Empty / New Session State**:
   - Clean, centered greeting with quick prompt suggestion chips and thread configuration summary.
2. **Message Bubbles**:
   - **User Message**: Sleek right-aligned bubble with subtle slate tint (`#F1F5F9` in light mode), clean typography, and optional media thumbnails.
   - **Assistant Message**: Full-width or left-aligned unboxed presentation on clean canvas, featuring a markdown renderer, syntax-highlighted code blocks with quick-copy buttons, and inline math/table support.
3. **Execution Action Pill (Post-Turn)**:
   - Placed directly between reasoning thought blocks and the final response (or at the bottom of the assistant message).
   - Summarizes actions: e.g., `⚡ Ran 3 tools (FileEdit, WebSearch) • 1.2k tokens • 840ms`.
   - Tapping the pill expands a step-by-step audit card detailing each tool invocation, input arguments, and completion status.
4. **Input Composer**:
   - Floating rounded container with integrated `+` attachment button on the left, multi-line auto-expanding text field, voice wave mic button, and animated send arrow on the right.

#### B. Gemini-Style Attachment Bottom Sheet
- Invoked via the `+` button in the chat input bar.
- Opens a rounded M3 bottom sheet displaying an organized 2-row grid (4 items per row) of squircle action cards:
  - **Row 1**: Camera (`#EFF6FF`), Photos/Gallery (`#F5F3FF`), Document Files (`#ECFDF5`), GitHub Repo (`#F8FAFC`).
  - **Row 2**: Workspace Artifacts (`#FFFBEB`), Knowledge Bits (`#FEF2F2`), Google Drive (`#F0FDF4`), Audio Settings (`#F3E8FF`).
- Each tile features a 56dp squircle container, vibrant dual-tone icon, and clear label with tactile ripple.

#### C. Workspace & Full-Screen File Explorer (Claude Artifacts Style)
1. **Code Tab Landing**:
   - Split into an interactive top bar (File name, Live generation indicator, Action menu: Save, Copy, Download, Server toggle, File Explorer trigger).
   - Sub-bar with mode switcher: **View / Artifact Preview** (HTML/Markdown/SVG rendered view) vs. **Raw Editor** (monospaced syntax editor with line numbers).
2. **Full-Screen Modern File Manager**:
   - Tapping the folder icon opens a full-screen or large modal file manager view.
   - **Header**: Workspace title, active branch/folder breadcrumb trail, search filter bar, and file-tree statistics (total files, size).
   - **Directory Cards**: Folders render as expandable cards showing contained item counts, nested children with indented indentation guides, and color-coded file icons (e.g., Kotlin blue, JSON yellow, Markdown green).
   - **Quick Action Bar**: Fast switches for New File, New Folder, and Git PRs/Issues.

#### D. Elevated Bottom Navigation
- Preserves the current layout: centered pill toggle with `Chat` and `Code` buttons, flanked by a 3-dots more-options action.
- Upgraded with:
  - Floating pill geometry with smooth animated indicator transition (`animateDpAsState` / `animateColorAsState`).
  - Tactile selection states with bold typography and active tint.
  - Safe-area bottom navigation bar padding to prevent gesture-pill collisions.

### Visual Identity & Theme Tokens (Light Mode Default)

| Design Token | Light Theme Value (Default) | Dark Theme Value (Fallback) | Description |
|---|---|---|---|
| `background` | `#F8FAFC` (Slate 50) | `#0B0F19` (Deep Navy) | App canvas background |
| `surface` | `#FFFFFF` (Pure White) | `#131B2E` (Surface Navy) | Cards, bottom bar, sheets |
| `surfaceVariant` | `#F1F5F9` (Slate 100) | `#1E293B` (Slate 800) | Secondary containers & pills |
| `primary` | `#2563EB` (Blue 600) | `#60A5FA` (Blue 400) | Primary actions & active tabs |
| `secondary` | `#475569` (Slate 600) | `#94A3B8` (Slate 400) | Supporting icons & subtext |
| `onSurface` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | Primary text (high contrast) |
| `outline` | `#E2E8F0` (Slate 200) | `#334155` (Slate 700) | Subtle borders and dividers |
| `accentPill` | `#DBEAFE` (Blue 100) | `#1E3A8A` (Blue 900) | Active pill background |

### Motion & Haptics
- **Bottom Navigation Transition**: Smooth crossfade slide when switching between `Chat` and `Code`.
- **Action Trail**: Spring expansion (`spring(stiffness = Spring.StiffnessMediumLow)`) when revealing tool execution logs.
- **Attachment Sheet**: Fluid sheet expand with staggered item alpha entry.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Full-Screen Tree Cards vs. Drawer Sidebar for File Explorer
- **Chosen Approach**: Transition from the narrow 300dp right-side drawer to a full-screen, card-based modal file explorer with search and breadcrumbs.
- **Why**: Deep directory trees and multi-level files on mobile screens suffer severe text clipping in a 300dp sidebar. A card-based full-screen layout gives breathing room for file sizes, timestamps, and quick action menus.
- **Alternatives Considered**: Retaining the right drawer with smaller font (rejected: poor touch targets and cramped layout).

### Decision 2: Google AI Studio Action Steps Location
- **Chosen Approach**: Render a compact, collapsible action badge immediately below the thought process and above the assistant's final response text.
- **Why**: Mirrors Google AI Studio and Anthropic Claude workflows, allowing developers to verify which tools executed and what files were read without cluttering the chat with raw JSON.
- **Alternatives Considered**: Floating toast or separate debugger tab (rejected: disconnected from the turn context).

### Decision 3: Preserving Fixed Bottom Navigation Geometry
- **Chosen Approach**: Keep the pill-shaped bottom toggle bar with `Chat`, `Code`, and `More` in their current positions, but rebuild its styling with Material 3 elevation and animated indicators.
- **Why**: Meets the user's explicit requirement ("Keeping current buttons and placement of them") while resolving visual clumsiness.

---

## 4. Technical Architecture & Component Hierarchy

### Component Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              OmniRootApp                               │
│  (Theme: Light Default • WindowInsets: statusBars + navigationBars)     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
           ┌────────────────────────┴────────────────────────┐
           ▼                                                 ▼
┌───────────────────────┐                         ┌───────────────────────┐
│     GlobalSidebar     │                         │    Scaffold Host      │
│  - Workspace switcher │                         │  - Content viewport   │
│  - Pinned threads     │                         │  - FixedBottomNav     │
│  - Knowledge bits     │                         │  - Floating Log FAB   │
└───────────────────────┘                         └───────────┬───────────┘
                                                              │
                     ┌────────────────────────────────────────┴─────────┐
                     ▼                                                  ▼
     ┌───────────────────────────────┐                  ┌───────────────────────────────┐
     │          ChatScreen           │                  │          CodeScreen           │
     │ - Message list (LazyColumn)   │                  │ - Editor & Preview TopAppBar  │
     │ - ThoughtBubble (Collapsible) │                  │ - Artifact View / Raw Switch  │
     │ - ActionStepsPill (AI Studio) │                  │ - CodeEditor & Live Streamer  │
     │ - InputComposer (Floating)    │                  │ - Fullscreen File Explorer    │
     │ - AttachmentSheet (Gemini)    │                  │   • Breadcrumbs & Search Bar  │
     └───────────────────────────────┘                  │   • Collapsible Tree Cards    │
                                                        │   • File Type Badges & Actions│
                                                        └───────────────────────────────┘
```

### Component Breakdown & Module Targets

1. **`MyApplicationTheme` (`Theme.kt`)**:
   - Ensure Light theme is strictly initialized as the default scheme.
   - Refine color tokens (`surfaceVariant`, `outline`, `primaryContainer`, `onSurface`) for higher contrast and slate-toned minimalism.

2. **`FixedBottomNav.kt`**:
   - Upgrade pill container with smooth rounded styling, border stroke (`#E2E8F0`), and animated tab indicator.
   - Ensure `navigationBarsPadding()` is properly applied to prevent gesture navigation interference.

3. **`AttachmentPickerBottomSheet.kt`**:
   - Rebuild layout using a 4-column x 2-row `LazyVerticalGrid` / `Row` configuration.
   - Render squircle tiles (56dp x 56dp) with rounded corners (16dp), pastel tint backgrounds, and concise labels.

4. **`ActionStepsPill.kt` (New Component in `com.example.ui.chat`)**:
   - Collapsible chip displaying tool execution summary (e.g., `⚡ 2 actions executed • 1.4k tokens`).
   - Expanded state shows sequential step cards: tool name, execution status badge, input summary, and elapsed time.

5. **`FileExplorer.kt` & `CodeScreen.kt`**:
   - Refactor file explorer into a spacious, full-screen card-based view with breadcrumbs, real-time search, file-type icons, and collapsible folder cards.
   - Retain Claude-style Artifact viewer controls (View vs. Raw Edit) in the sub-bar.

---

## 5. Implementation Roadmap (Phased Execution)

- **Phase A**: Refine Light Theme tokens & modernize `FixedBottomNav` pill toggle.
- **Phase B**: Implement `ActionStepsPill` in `ChatScreen` for Google AI Studio execution trails.
- **Phase C**: Redesign `AttachmentPickerBottomSheet` into Gemini 2-row squircle grid.
- **Phase D**: Overhaul `FileExplorer` into full-screen collapsible tree cards with breadcrumbs and type badges.
- **Phase E**: Compilation verification, receipts audit logging, and on-device testing guide.
