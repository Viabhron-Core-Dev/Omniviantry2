# Google AI Studio Build UI Polish: Chat & Code Tabs

Architectural design, UI comparison, and surgical polish plan to align the mobile app's **Chat** and **Code** (Workspace) tabs with the modern **Google AI Studio Build** aesthetic, strictly respecting the user's requirement to polish the existing architecture without disruptive redesigns.

---

### User Direction & Confirmed Guardrails

- **Core Intent**: Align visual aesthetic and micro-interactions with Google AI Studio's Build interface, specifically focusing on the **Chat** and **Code** tabs.
- **Decision 1 (Navigation & Tab Switcher)**: Keep the existing bottom pill switcher location and structure. Refine the label to "Code" (matching Google AI Studio's "Chat | Code" pair) and polish the pill toggle's typography, container stroke, and smooth sliding indicator.
- **Decision 2 (Mobile File Navigation)**: Preserve the existing layout and mode switcher (full-screen file explorer toggle + active editor viewport + horizontal open-tabs dock). Polish visual hierarchy, tree indentations, file icon color accents, and breadcrumbs.
- **Decision 3 (Visual Aesthetic)**: Maintain the existing Material 3 IDE theme while elevating typography, surface elevations, divider subtlety, and icon button consistency to match Google AI Studio's clean, minimalist toolbars.

---

## 1. UI Comparison & Gap Analysis

| Dimension | Current App Implementation | Google AI Studio (Build UI) Standard | Planned Polish |
| :--- | :--- | :--- | :--- |
| **Tab Switcher** | Bottom pill labeled `Chat` and `Workspace` in `FixedBottomNav.kt`. | Segmented pill labeled `Chat` and `Code` with crisp border and smooth indicator. | Update tab label text to `Code` (retaining `AppTab.WORKSPACE` compatibility), polish pill elevation, border stroke (`outlineVariant`), and animated pill selection. |
| **Code Header & Breadcrumb** | Standard `TopAppBar` showing just file name or "Workspace Explorer", standard action buttons. | Minimalist IDE header showing breadcrumb path (`workspace / src / ... / file.kt`), file state, and compact action icons. | Introduce sleek breadcrumb header row in `CodeScreen.kt` with subtle path tokens, file format badge, and cleaner action grouping. |
| **File Tabs Dock** | Chrome-Android style carousel cards at bottom with rounded rectangles and `(_)` minimize tag. | Clean horizontal file tab strip with subtle border separators, file type icons, unsaved dot indicator, and hover/pressed ripples. | Polish `LazyRow` tab bar into modern IDE tab bar: clean rectangular/rounded tabs with subtle active indicator bar, consistent file-type tints, and refined close button. |
| **Viewer Toolbar** | Chunky secondary surface row with SegmentedButtons (`View` vs `Raw`), "AI" staging button, and minimize/close icons. | Integrated editor sub-header with subtle ghost buttons, streamlined mode toggle, and compact AI prompt badge. | Refine control bar into a unified slim toolstrip with polished tonal buttons, cleaner typography, and unified padding (8dp vertical). |
| **File Explorer** | Functional tree list with card rows, swipe-to-comment, and open-with menus. | Dense, organized file tree with subtle indentation guidelines, file extension colored badges, search bar, and clean fold chevrons. | Add subtle hierarchical guide indent lines, file type tint badges, and search/filter bar polish in `FileExplorer.kt`. |
| **Chat Composer & Artifact Shelf** | Floating chip row above text field for staged files and comments. | Elevated staged context shelf with preview thumbnail/snippet, line number pill, and clean dismiss tag. | Enhance staged reference chips in `ChatScreen.kt` with monospaced line badges, truncated comment preview, and border accent. |

---

## 2. GPS Protocol: Stress Test, Push Back & Trade-Offs

### ⚠️ Gaslight (Technical Consequences of Bad Decisions)
- **Over-Desktopizing Mobile Viewports**: Desktop AI Studio uses a multi-pane split view (Chat on left, File Explorer in middle-left, Code Editor in center, Preview on right). Attempting a side-by-side split on mobile screens (<420dp width) leads to extreme horizontal scrolling, truncated line wraps, unreadable code blocks, and constant accidental touches. Preserving the single-viewport toggle between Explorer and Editor is critical for mobile ergonomics.
- **Excessive Recompositions on Tab Switching**: Rebuilding the file tree or reloading editor syntax trees on every pill tap will trigger noticeable frame drops and keyboard flickers. Polish must remain purely visual and state-preserving.

### 🛡️ Push Back (Constructive Challenges)
- **"Workspace" vs "Code" naming**: The user specifically requested: *"look like google ai studio (your) new look. But only chat and code tab."* Currently the second tab is named `Workspace`. Changing the user-facing label to `Code` strictly honors the AI Studio identity while keeping internal enums and files backwards-compatible.
- **Top vs Bottom Tab Switcher**: Desktop AI Studio places "Chat" and "Code" at the top header or side rail. On mobile, top-screen tab switchers are ergonomic anti-patterns for single-handed use (difficult thumb reach on 6.5"+ phones). Retaining the bottom floating pill is significantly superior for mobile UX while adopting AI Studio's visual styling.

### 🔍 Stress Test & Bias Sweep
- **Confirmation Bias Check**: Did we verify that users editing non-code files (images, audio, PDF) aren't confused by a tab named "Code"? Yes: In Google AI Studio, the "Code" tab houses all project files and artifacts (assets, scripts, configs).
- **Recency Bias Check**: Do not strip the recently completed Phase 16 features (Contextual Comments, Pass to AI staging basket) during the UI polish. The polish must showcase these buttons with better visual integration rather than replacing them.

---

## 3. Surgical Implementation Phases

### Phase 1: Tab Switcher & Navigation Polish (`FixedBottomNav.kt`)
- Rename display label `Workspace` -> `Code` in `TabButton`.
- Refine pill container styling:
  - Match Google AI Studio's subtle border (`outlineVariant.copy(alpha = 0.35f)`).
  - Background styling: `surfaceContainer` with refined tonal elevation.
  - Active tab indicator: Pill container with high-contrast label and smooth spring animation.
  - Preserve 3-dots more menu strictly in place.

### Phase 2: Code Screen Header & Breadcrumb Bar (`CodeScreen.kt`)
- Refine `TopAppBar`:
  - Show breadcrumb navigation (e.g. `workspace / app / src / ... / MainActivity.kt`) when a file is open.
  - Compact right-side action buttons: Save (`Icons.Default.Save`), Copy (`Icons.Default.ContentCopy`), Download, and Toggle Explorer with consistent 40dp touch targets.
  - Refine sub-header toolbar: Mode switcher (`View` / `Raw`), AI Staging button (`@artifact`), and Comment button (`AddComment`) with clean unified styling.

### Phase 3: File Tab Dock Polish (`CodeScreen.kt`)
- Refine bottom open-files carousel:
  - Transition from chunky floating cards to modern IDE-style tab strip.
  - Active tab highlight: subtle bottom accent line or container elevation matching Google AI Studio IDE tabs.
  - Clean close 'x' icon (`Icons.Default.Close`) with generous touch padding.
  - Dirty/unsaved indicator dot when local edits are unsaved.

### Phase 4: File Explorer Hierarchy Polish (`FileExplorer.kt`)
- Elevate file list items:
  - Clear indentation guides for nested directories.
  - File extension color accent dots (e.g. Kotlin = purple, XML = orange, JSON/MD = cyan).
  - Clean folder collapse/expand chevron arrows (`Icons.Default.ChevronRight` / `ExpandMore`).
  - Retain the right-swipe comment action and context menus.

### Phase 5: Verification & Safety Scan
- Proactive compilation check via `compile_applet`.
- Silent security scan for credentials and keystores.
- On-device verification flow documentation.

---

## 4. Exact Files Touched (Surgical Scope)

1. `app/src/main/java/com/example/ui/bottomnav/FixedBottomNav.kt`
   - Tab label update to "Code", pill aesthetic refinement.
2. `app/src/main/java/com/example/ui/code/CodeScreen.kt`
   - Breadcrumb header, sub-header action strip, and bottom file tab dock styling.
3. `app/src/main/java/com/example/ui/code/FileExplorer.kt`
   - Visual polish of tree item rows, hierarchy guides, and badges.
4. `current plan/BLUEPRINT.md` & `receipts/RECEIPTS_118.md`
   - Audit trail and blueprint update.
